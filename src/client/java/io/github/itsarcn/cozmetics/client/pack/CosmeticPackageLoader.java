package io.github.itsarcn.cozmetics.client.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.itsarcn.cozmetics.Cozmetics;
import io.github.itsarcn.cozmetics.bbmodel.BbModel;
import io.github.itsarcn.cozmetics.bbmodel.BbModelManager;
import io.github.itsarcn.cozmetics.client.render.dynamic.GeckoLibDynamicBakeHelper;
import io.github.itsarcn.cozmetics.client.texture.CosmeticTextureManager;
import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticDefinition;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticRegistry;
import io.github.itsarcn.cozmetics.pack.CosmeticPackageManifest;
import io.github.itsarcn.cozmetics.pack.CosmeticPackageValidator;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Safely extracts, adapts, and registers cosmetic ZIP packages at runtime.
 *
 * Supports both Blockbench (.bbmodel) projects and Bedrock (.geo.json / .animation.json) models.
 * Feeds the dynamic rendering, texture decoding, and GeckoLib animation pipelines.
 */
public final class CosmeticPackageLoader {

	private CosmeticPackageLoader() {}

	/**
	 * Safely extracts a verified cosmetic ZIP package into the specified target directory.
	 *
	 * Enforces canonical path verification to ensure no extracted file escapes the target directory.
	 */
	public static void safeExtract(Path zipPath, Path targetDir) throws IOException {
		Files.createDirectories(targetDir);
		Path normalizedTargetDir = targetDir.toRealPath();

		try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String entryName = entry.getName();

				Path resolved = targetDir.resolve(entryName).normalize();
				// Strict ZIP Slip protection
				if (!resolved.startsWith(normalizedTargetDir) && !resolved.startsWith(targetDir.normalize())) {
					throw new SecurityException("ZIP slip attempt detected! Entry: " + entryName);
				}

				if (entry.isDirectory()) {
					Files.createDirectories(resolved);
				} else {
					if (resolved.getParent() != null) {
						Files.createDirectories(resolved.getParent());
					}
					try (InputStream in = zipFile.getInputStream(entry)) {
						Files.copy(in, resolved, StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
		}
	}

	/**
	 * Validates, extracts, and loads a cosmetic ZIP package into the runtime registry and renderers.
	 *
	 * @param zipPath Path to the downloaded ZIP package
	 * @param extractedDir Destination directory to extract into
	 * @param name Display name from catalogue metadata
	 * @param category Category from catalogue metadata
	 * @param expectedHash Expected SHA-256 hash (or null to skip hash verification)
	 * @return The registered Cosmetic
	 */
	public static Cosmetic loadPackage(
			Path zipPath,
			Path extractedDir,
			String name,
			CosmeticCategory category,
			@Nullable String expectedHash
	) throws Exception {
		// 1. Validate security and integrity
		CosmeticPackageValidator.ValidationResult validation =
				CosmeticPackageValidator.validatePackage(zipPath, expectedHash);
		if (!validation.valid()) {
			throw new IllegalArgumentException("Package validation failed: " + validation.message());
		}

		// 2. Safely extract files
		safeExtract(zipPath, extractedDir);

		// 3. Load from extracted files
		return loadFromExtracted(extractedDir, name, category);
	}

	/**
	 * Loads a cosmetic directly from an already extracted package directory.
	 *
	 * Decouples catalogue metadata (name, category) from package 3D assets so metadata updates
	 * take effect immediately without re-extracting or re-downloading.
	 */
	public static Cosmetic loadFromExtracted(Path extractedDir, String name, CosmeticCategory category) throws Exception {
		Path manifestFile = extractedDir.resolve("manifest.json");
		if (!Files.exists(manifestFile)) {
			throw new IOException("Missing manifest.json in extracted cosmetic directory: " + extractedDir);
		}

		String manifestJson = Files.readString(manifestFile, StandardCharsets.UTF_8);
		CosmeticPackageManifest manifest = CosmeticPackageManifest.parse(manifestJson);

		Identifier id = manifest.id();
		Path modelFile = extractedDir.resolve(manifest.modelPath());
		if (!Files.exists(modelFile)) {
			throw new IOException("Model file '" + manifest.modelPath() + "' not found in package " + id);
		}

		Path textureFile = extractedDir.resolve(manifest.texturePath());
		if (!Files.exists(textureFile)) {
			throw new IOException("Texture file '" + manifest.texturePath() + "' not found in package " + id);
		}

		// Register texture dynamically
		try {
			CosmeticTextureManager.getInstance().loadFromFile(id, textureFile);
		} catch (Throwable t) {
			Cozmetics.LOGGER.warn("Could not register texture for '{}': {}", id, t.getMessage());
		}

		// Load and adapt model
		String modelFileName = modelFile.getFileName().toString().toLowerCase();
		if (modelFileName.endsWith(".bbmodel")) {
			try (InputStream in = Files.newInputStream(modelFile)) {
				BbModel bbModel = BbModelManager.getInstance().load(id, in).orElseThrow(
						() -> new IOException("Failed to parse .bbmodel for " + id)
				);

				JsonObject geoJson = BbModelManager.getInstance().getGeometryJson(id);
				JsonObject animJson = BbModelManager.getInstance().getAnimationJson(id);
				if (geoJson != null) {
					GeckoLibDynamicBakeHelper.bakeModel(id, geoJson);
				}
				if (animJson != null) {
					GeckoLibDynamicBakeHelper.bakeAnimations(id, animJson);
				}
			}
		} else if (modelFileName.endsWith(".json")) {
			// Bedrock geometry JSON
			JsonObject geoJson = JsonParser.parseString(Files.readString(modelFile, StandardCharsets.UTF_8)).getAsJsonObject();
			GeckoLibDynamicBakeHelper.bakeModel(id, geoJson);

			if (manifest.animationPath() != null) {
				Path animFile = extractedDir.resolve(manifest.animationPath());
				if (Files.exists(animFile)) {
					JsonObject animJson = JsonParser.parseString(Files.readString(animFile, StandardCharsets.UTF_8)).getAsJsonObject();
					GeckoLibDynamicBakeHelper.bakeAnimations(id, animJson);
				}
			}
		} else {
			throw new UnsupportedOperationException("Unsupported 3D model format: " + modelFileName);
		}

		// Build and register definition
		CosmeticDefinition.Builder builder = CosmeticDefinition.builder(id, category, name)
				.model(id)
				.texture(id)
				.slot(manifest.slot())
				.transform(manifest.transform());

		if (manifest.animationPath() != null || manifest.idleAnimationName() != null) {
			Identifier animId = manifest.animationPath() != null ? id : null;
			builder.animation(animId, manifest.idleAnimationName());
		}

		CosmeticDefinition def = builder.build();
		CosmeticRegistry.register(def);

		Cozmetics.LOGGER.info("Successfully loaded and registered cosmetic '{}' ({}) from package", id, name);
		return def;
	}
}

