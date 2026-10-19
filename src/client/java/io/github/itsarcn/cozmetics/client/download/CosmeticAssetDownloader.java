package io.github.itsarcn.cozmetics.client.download;

import io.github.itsarcn.cozmetics.Cozmetics;
import io.github.itsarcn.cozmetics.cache.CosmeticCacheManager;
import io.github.itsarcn.cozmetics.catalog.CatalogItem;
import io.github.itsarcn.cozmetics.client.pack.CosmeticPackageLoader;
import io.github.itsarcn.cozmetics.client.texture.CosmeticTextureManager;
import io.github.itsarcn.cozmetics.client.texture.ImageFormat;
import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-performance, non-blocking asynchronous asset downloader for cosmetic ZIP packages
 * and static thumbnails.
 *
 * Never blocks the Minecraft render thread. Integrates local disk cache checks, SHA-256
 * verification, safe package extraction, and automatic registration into runtime renderers.
 */
public class CosmeticAssetDownloader {

	private static CosmeticAssetDownloader instance;

	private final HttpClient httpClient;
	private final ExecutorService executor;
	private final CosmeticCacheManager cacheManager;

	private final Map<Identifier, CompletableFuture<Cosmetic>> activePackageDownloads = new ConcurrentHashMap<>();
	private final Map<Identifier, CompletableFuture<Identifier>> activeThumbnailDownloads = new ConcurrentHashMap<>();

	public static synchronized CosmeticAssetDownloader getInstance() {
		if (instance == null) {
			instance = new CosmeticAssetDownloader(CosmeticCacheManager.getInstance());
		}
		return instance;
	}

	public CosmeticAssetDownloader(CosmeticCacheManager cacheManager) {
		this.cacheManager = cacheManager;
		this.executor = Executors.newFixedThreadPool(4, runnable -> {
			Thread t = new Thread(runnable, "Cozmetics-Asset-Downloader");
			t.setDaemon(true);
			return t;
		});

		this.httpClient = HttpClient.newBuilder()
				.executor(this.executor)
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	/**
	 * Downloads, verifies, and loads a cosmetic ZIP package on-demand when the user
	 * selects Preview or Equip.
	 *
	 * Reuses locally cached and verified files if the version and hash match.
	 */
	public CompletableFuture<Cosmetic> downloadAndLoadPackageAsync(CatalogItem item) {
		Identifier id = item.id();

		// Check if already in progress to avoid duplicate network transfers
		CompletableFuture<Cosmetic> ongoing = activePackageDownloads.get(id);
		if (ongoing != null && !ongoing.isDone()) {
			return ongoing;
		}

		CompletableFuture<Cosmetic> future = CompletableFuture.supplyAsync(() -> {
			try {
				Path extractedDir = cacheManager.getExtractedPackageDir(id);

				// 1. Cache hit check
				if (cacheManager.isPackageCachedAndValid(id, item.version(), item.hash())) {
					Cozmetics.LOGGER.info("Loading cosmetic '{}' from local cache", id);
					return CosmeticPackageLoader.loadFromExtracted(extractedDir, item.name(), item.category());
				}

				// 2. Download ZIP package
				if (item.packageUrl() == null || item.packageUrl().isBlank()) {
					throw new IllegalStateException("No package URL provided for cosmetic " + id);
				}

				Cozmetics.LOGGER.info("Downloading cosmetic package for '{}' from {}", id, item.packageUrl());
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(item.packageUrl()))
						.timeout(Duration.ofSeconds(20))
						.GET()
						.build();

				HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
				if (response.statusCode() < 200 || response.statusCode() >= 300) {
					throw new IOException("HTTP download failed with status " + response.statusCode());
				}

				byte[] zipBytes = response.body();
				Path zipPath = cacheManager.getPackageFile(id);
				Files.write(zipPath, zipBytes);

				// 3. Clean old extracted folder and extract new verified package
				cacheManager.cleanExtracted(id);
				Cosmetic cosmetic = CosmeticPackageLoader.loadPackage(
						zipPath,
						extractedDir,
						item.name(),
						item.category(),
						item.hash()
				);

				// 4. Cache maintenance
				cacheManager.pruneCacheIfExceedsLimit(CosmeticCacheManager.DEFAULT_MAX_CACHE_BYTES);

				return cosmetic;
			} catch (Exception e) {
				Cozmetics.LOGGER.error("Failed to download and load package for '{}'", id, e);
				throw new RuntimeException(e);
			}
		}, this.executor);

		activePackageDownloads.put(id, future);
		future.whenComplete((res, ex) -> activePackageDownloads.remove(id));
		return future;
	}

	/**
	 * Asynchronously fetches, caches, and registers a static PNG or WebP thumbnail for
	 * a cosmetic card.
	 *
	 * Returns immediately with a placeholder or cached texture without blocking the render thread.
	 */
	public CompletableFuture<Identifier> loadThumbnailAsync(CatalogItem item) {
		Identifier id = item.id();
		Identifier thumbId = Identifier.of(id.getNamespace(), "textures/thumbnails/" + id.getPath());

		CompletableFuture<Identifier> ongoing = activeThumbnailDownloads.get(id);
		if (ongoing != null && !ongoing.isDone()) {
			return ongoing;
		}

		CompletableFuture<Identifier> future = CompletableFuture.supplyAsync(() -> {
			try {
				// 1. Check disk cache for existing thumbnail
				Path cachedPng = cacheManager.getThumbnailFile(id, "png");
				Path cachedWebp = cacheManager.getThumbnailFile(id, "webp");

				Path existingFile = Files.exists(cachedPng) ? cachedPng : (Files.exists(cachedWebp) ? cachedWebp : null);
				if (existingFile != null) {
					CosmeticTextureManager.getInstance().loadFromFile(thumbId, existingFile);
					return thumbId;
				}

				// 2. Download from remote thumbnail URL
				if (item.thumbnailUrl() == null || item.thumbnailUrl().isBlank()) {
					return getDefaultPlaceholderId();
				}

				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(item.thumbnailUrl()))
						.timeout(Duration.ofSeconds(10))
						.GET()
						.build();

				HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
				if (response.statusCode() == 200) {
					byte[] bytes = response.body();
					ImageFormat format = ImageFormat.detect(bytes);
					String ext = (format == ImageFormat.WEBP) ? "webp" : "png";
					Path targetFile = cacheManager.getThumbnailFile(id, ext);
					Files.write(targetFile, bytes);

					CosmeticTextureManager.getInstance().loadFromFile(thumbId, targetFile);
					return thumbId;
				}
			} catch (Exception e) {
				Cozmetics.LOGGER.debug("Thumbnail fetch failed for '{}': {}", id, e.getMessage());
			}

			return getDefaultPlaceholderId();
		}, this.executor);

		activeThumbnailDownloads.put(id, future);
		future.whenComplete((res, ex) -> activeThumbnailDownloads.remove(id));
		return future;
	}

	public static Identifier getDefaultPlaceholderId() {
		return Identifier.of("cozmetics", "textures/gui/placeholder.png");
	}
}

