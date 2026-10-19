package io.github.itsarcn.cozmetics.cache;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.itsarcn.cozmetics.Cozmetics;
import io.github.itsarcn.cozmetics.pack.CosmeticPackageValidator;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Manages the local bounded disk cache for cosmetic packages, thumbnails, and metadata.
 *
 * Enforces safe path isolation under `<gameDir>/cozmetics/cache/`, version checks,
 * SHA-256 integrity validation, and bounded total disk consumption.
 */
public class CosmeticCacheManager {

	public static final long DEFAULT_MAX_CACHE_BYTES = 250L * 1024L * 1024L; // 250 MB
	private static CosmeticCacheManager instance;

	private final Path cacheRoot;
	private final Path packagesDir;
	private final Path extractedDir;
	private final Path thumbnailsDir;
	private final Path metadataDir;

	public static synchronized CosmeticCacheManager getInstance() {
		if (instance == null) {
			Path root;
			try {
				root = FabricLoader.getInstance().getGameDir().resolve("cozmetics").resolve("cache");
			} catch (Throwable t) {
				root = Path.of("build/tmp/cozmetics-cache");
			}
			instance = new CosmeticCacheManager(root);
		}
		return instance;
	}

	public CosmeticCacheManager(Path cacheRoot) {
		this.cacheRoot = cacheRoot;
		this.packagesDir = cacheRoot.resolve("packages");
		this.extractedDir = cacheRoot.resolve("extracted");
		this.thumbnailsDir = cacheRoot.resolve("thumbnails");
		this.metadataDir = cacheRoot.resolve("metadata");

		createDirectoriesSafely(packagesDir);
		createDirectoriesSafely(extractedDir);
		createDirectoriesSafely(thumbnailsDir);
		createDirectoriesSafely(metadataDir);
	}

	private void createDirectoriesSafely(Path dir) {
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			Cozmetics.LOGGER.warn("Could not create cache directory '{}': {}", dir, e.getMessage());
		}
	}

	public Path getCacheRoot() {
		return cacheRoot;
	}

	public Path getPackagesDir() {
		return packagesDir;
	}

	public Path getExtractedDir() {
		return extractedDir;
	}

	public Path getThumbnailsDir() {
		return thumbnailsDir;
	}

	public Path getMetadataDir() {
		return metadataDir;
	}

	public static String sanitizeIdentifier(Identifier id) {
		return id.getNamespace() + "_" + id.getPath().replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	public Path getPackageFile(Identifier id) {
		return packagesDir.resolve(sanitizeIdentifier(id) + ".zip");
	}

	public Path getExtractedPackageDir(Identifier id) {
		return extractedDir.resolve(sanitizeIdentifier(id));
	}

	public Path getThumbnailFile(Identifier id, String extension) {
		String ext = extension.startsWith(".") ? extension : "." + extension;
		return thumbnailsDir.resolve(sanitizeIdentifier(id) + ext);
	}

	public Path getMetadataFile() {
		return metadataDir.resolve("catalogue.json");
	}

	/**
	 * Checks if a cosmetic package has already been downloaded, extracted, and matches
	 * the specified version and SHA-256 hash.
	 */
	public boolean isPackageCachedAndValid(Identifier id, String expectedVersion, @Nullable String expectedHash) {
		Path dir = getExtractedPackageDir(id);
		Path manifestFile = dir.resolve("manifest.json");
		if (!Files.exists(manifestFile)) {
			return false;
		}

		try {
			String manifestStr = Files.readString(manifestFile, StandardCharsets.UTF_8);
			JsonObject json = JsonParser.parseString(manifestStr).getAsJsonObject();
			String cachedVersion = json.has("version") ? json.get("version").getAsString() : "1.0.0";

			// Version check
			if (!cachedVersion.equals(expectedVersion)) {
				return false;
			}

			// SHA-256 hash check on package zip if provided
			if (expectedHash != null && !expectedHash.isBlank()) {
				Path zipFile = getPackageFile(id);
				if (Files.exists(zipFile)) {
					String actualHash = CosmeticPackageValidator.computeSha256(zipFile);
					if (!actualHash.equalsIgnoreCase(expectedHash.trim())) {
						return false;
					}
				}
			}

			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Cleans the extracted folder for a specific cosmetic to allow re-extraction or clean updates.
	 */
	public void cleanExtracted(Identifier id) {
		Path dir = getExtractedPackageDir(id);
		if (Files.exists(dir)) {
			try (var stream = Files.walk(dir)) {
				stream.sorted(Comparator.reverseOrder())
						.map(Path::toFile)
						.forEach(java.io.File::delete);
			} catch (IOException e) {
				Cozmetics.LOGGER.warn("Failed to clean extracted cache for '{}': {}", id, e.getMessage());
			}
		}
	}

	/**
	 * Bounded cache eviction: ensures total cache directory size does not exceed the limit.
	 * If exceeded, evicts the oldest ZIP packages first.
	 */
	public synchronized void pruneCacheIfExceedsLimit(long maxBytes) {
		try {
			long totalSize = calculateDirectorySize(cacheRoot);
			if (totalSize <= maxBytes) {
				return;
			}

			// Gather ZIP packages ordered by oldest lastModified
			List<Path> packageFiles = new ArrayList<>();
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(packagesDir, "*.zip")) {
				for (Path p : ds) {
					packageFiles.add(p);
				}
			}

			packageFiles.sort(Comparator.comparingLong(p -> {
				try {
					return Files.getLastModifiedTime(p).toMillis();
				} catch (IOException e) {
					return 0L;
				}
			}));

			for (Path pkg : packageFiles) {
				long size = Files.size(pkg);
				Files.deleteIfExists(pkg);
				totalSize -= size;
				Cozmetics.LOGGER.info("Evicted old cached package: {}", pkg.getFileName());
				if (totalSize <= maxBytes) {
					break;
				}
			}
		} catch (Exception e) {
			Cozmetics.LOGGER.warn("Error during cache pruning: {}", e.getMessage());
		}
	}

	private static long calculateDirectorySize(Path dir) throws IOException {
		if (!Files.exists(dir)) return 0L;
		final long[] size = {0L};
		Files.walkFileTree(dir, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				size[0] += attrs.size();
				return FileVisitResult.CONTINUE;
			}
		});
		return size[0];
	}
}

