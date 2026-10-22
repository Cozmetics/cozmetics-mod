package io.github.itsarcn.cozmetics.client.texture;

import io.github.itsarcn.cozmetics.Cozmetics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal Texture Manager for Cozmetics.
 *
 * Manages the lifecycle of decoded static and animated textures, registers them
 * into Minecraft's TextureManager as NativeImageBackedTextures, and provides time-accurate
 * frame sampling for both GUI thumbnails and 3D in-game entity rendering.
 */
public final class CosmeticTextureManager implements AutoCloseable {
	private static final CosmeticTextureManager INSTANCE = new CosmeticTextureManager();

	public static CosmeticTextureManager getInstance() {
		return INSTANCE;
	}

	private final Map<Identifier, TextureEntry> textures = new ConcurrentHashMap<>();

	private CosmeticTextureManager() {}

	/**
	 * Represents a decoded and GPU-registered cosmetic texture (single or multi-frame).
	 */
	public static class TextureEntry implements AutoCloseable {
		private final Identifier baseId;
		private final int width;
		private final int height;
		private final List<Identifier> frameIds;
		private final List<NativeImageBackedTexture> gpuTextures;
		private final int[] frameDurations;
		private final int totalDurationMs;

		public TextureEntry(Identifier baseId, DecodedImage decoded, TextureManager tm) {
			this.baseId = baseId;
			this.width = decoded.getWidth();
			this.height = decoded.getHeight();

			int frameCount = decoded.getFrameCount();
			this.frameIds = new ArrayList<>(frameCount);
			this.gpuTextures = new ArrayList<>(frameCount);
			this.frameDurations = new int[frameCount];

			int total = 0;
			for (int i = 0; i < frameCount; i++) {
				DecodedFrame frame = decoded.getFrames().get(i);
				this.frameDurations[i] = frame.durationMs();
				total += frame.durationMs();

				Identifier frameId = Identifier.of(baseId.getNamespace(), baseId.getPath() + "_dyn_f" + i);
				this.frameIds.add(frameId);

				NativeImage frameImage = frame.image();
				NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "Cozmetics_" + frameId, frameImage);
				this.gpuTextures.add(texture);

				// Register in Minecraft TextureManager on main thread if available
				if (tm != null) {
					tm.registerTexture(frameId, texture);
					// Also register under baseId for frame 0 (so any model/renderer querying baseId directly finds it)
					if (i == 0) {
						tm.registerTexture(baseId, texture);
					}
				}
			}
			this.totalDurationMs = Math.max(1, total);
		}

		public int getWidth() {
			return width;
		}

		public int getHeight() {
			return height;
		}

		public int getFrameCount() {
			return frameIds.size();
		}

		public int getTotalDurationMs() {
			return totalDurationMs;
		}

		public boolean isAnimated() {
			return frameIds.size() > 1;
		}

		/**
		 * Returns the active frame Identifier for the specified timestamp in milliseconds.
		 */
		public Identifier getFrameAt(long timeMs) {
			if (frameIds.isEmpty()) {
				return baseId;
			}
			if (frameIds.size() == 1) {
				return baseId;
			}

			long modulo = Math.abs(timeMs) % totalDurationMs;
			long accum = 0;
			for (int i = 0; i < frameDurations.length; i++) {
				accum += frameDurations[i];
				if (modulo < accum) {
					return frameIds.get(i);
				}
			}
			return frameIds.getFirst();
		}

		@Override
		public void close() {
			for (NativeImageBackedTexture tex : gpuTextures) {
				try {
					tex.close();
				} catch (Exception ignored) {}
			}
			gpuTextures.clear();
			frameIds.clear();
		}
	}

	/**
	 * Checks if a texture has been loaded and registered in the GPU cache.
	 */
	public boolean hasTexture(Identifier id) {
		return textures.containsKey(id);
	}

	/**
	 * Gets or loads a texture from resources or fallback paths.
	 */
	public TextureEntry getOrLoadTexture(Identifier id) {
		return textures.computeIfAbsent(id, this::loadTextureInternal);
	}

	/**
	 * Gets the active texture Identifier for rendering at the current system time.
	 */
	public Identifier getTexturePath(Identifier baseId, long timeMs) {
		TextureEntry entry = getOrLoadTexture(baseId);
		if (entry != null) {
			return entry.getFrameAt(timeMs);
		}
		return baseId;
	}

	private TextureEntry loadTextureInternal(Identifier id) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && !client.isOnThread()) {
			try {
				return client.submit(() -> createTextureEntryInternal(id, client)).get(15, java.util.concurrent.TimeUnit.SECONDS);
			} catch (Exception e) {
				Cozmetics.LOGGER.error("Failed to load texture on render thread for '{}': {}", id, e.getMessage());
			}
		}
		return createTextureEntryInternal(id, client);
	}

	private TextureEntry createTextureEntryInternal(Identifier id, MinecraftClient client) {
		TextureManager tm = client != null ? client.getTextureManager() : null;

		// 1. Try reading from Minecraft ResourceManager
		if (client != null && client.getResourceManager() != null) {
			try {
				Optional<Resource> res = client.getResourceManager().getResource(id);
				if (res.isPresent()) {
					try (InputStream in = res.get().getInputStream()) {
						DecodedImage decoded = UniversalImageDecoder.decode(in, id.toString());
						return new TextureEntry(id, decoded, tm);
					}
				}
			} catch (Exception e) {
				Cozmetics.LOGGER.debug("Resource manager lookup failed for '{}': {}", id, e.getMessage());
			}
		}

		// 2. Try reading from classpath
		String pathInJar = "assets/" + id.getNamespace() + "/" + id.getPath();
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(pathInJar)) {
			if (in != null) {
				DecodedImage decoded = UniversalImageDecoder.decode(in, pathInJar);
				return new TextureEntry(id, decoded, tm);
			}
		} catch (Exception e) {
			Cozmetics.LOGGER.debug("Classpath lookup failed for '{}': {}", pathInJar, e.getMessage());
		}

		// 3. Fallback to placeholder
		Cozmetics.LOGGER.warn("Texture not found: '{}'; creating fallback placeholder.", id);
		DecodedImage placeholder = UniversalImageDecoder.createPlaceholder(ImageFormat.UNKNOWN);
		return new TextureEntry(id, placeholder, tm);
	}

	/**
	 * Explicitly loads an image file from disk and associates it with an Identifier.
	 *
	 * Decodes image on the calling thread, then safely registers the GPU texture on the
	 * Minecraft render thread to prevent OpenGL threading violations.
	 */
	public TextureEntry loadFromFile(Identifier id, Path filePath) throws Exception {
		MinecraftClient client = MinecraftClient.getInstance();

		try (InputStream in = Files.newInputStream(filePath)) {
			DecodedImage decoded = UniversalImageDecoder.decode(in, filePath.toString());

			if (client != null && !client.isOnThread()) {
				try {
					return client.submit(() -> {
						TextureEntry entry = new TextureEntry(id, decoded, client.getTextureManager());
						TextureEntry prev = textures.put(id, entry);
						if (prev != null) {
							prev.close();
						}
						return entry;
					}).get(15, java.util.concurrent.TimeUnit.SECONDS);
				} catch (Exception e) {
					Cozmetics.LOGGER.error("Failed to upload texture on render thread for '{}': {}", id, e.getMessage());
					throw e;
				}
			} else {
				TextureManager tm = client != null ? client.getTextureManager() : null;
				TextureEntry entry = new TextureEntry(id, decoded, tm);
				TextureEntry prev = textures.put(id, entry);
				if (prev != null) {
					prev.close();
				}
				return entry;
			}
		}
	}

	@Override
	public void close() {
		for (TextureEntry entry : textures.values()) {
			entry.close();
		}
		textures.clear();
	}
}

