package io.github.itsarcn.cozmetics.pack;

import io.github.itsarcn.cozmetics.Cozmetics;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Validates cosmetic ZIP packages before and during extraction.
 *
 * Enforces:
 * 1. Path traversal protection (preventing ZIP Slip vulnerability).
 * 2. ZIP bomb protection (limiting entry counts and total uncompressed bytes).
 * 3. File extension allowlisting (.json, .bbmodel, .png, .webp, .mcmeta).
 * 4. Manifest presence (manifest.json).
 * 5. SHA-256 integrity verification against catalogue hash.
 */
public final class CosmeticPackageValidator {

	public static final long MAX_PACKAGE_FILE_SIZE = 15 * 1024 * 1024; // 15 MB
	public static final long MAX_SINGLE_ENTRY_UNCOMPRESSED_SIZE = 10 * 1024 * 1024; // 10 MB
	public static final long MAX_TOTAL_UNCOMPRESSED_SIZE = 30 * 1024 * 1024; // 30 MB
	public static final int MAX_ENTRY_COUNT = 60;

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
			"json", "bbmodel", "png", "webp", "mcmeta"
	);

	public record ValidationResult(boolean valid, String message) {
		public static ValidationResult success() {
			return new ValidationResult(true, "Validation passed");
		}

		public static ValidationResult failure(String message) {
			return new ValidationResult(false, message);
		}
	}

	private CosmeticPackageValidator() {}

	/**
	 * Computes the SHA-256 hash of a file on disk.
	 */
	public static String computeSha256(Path file) throws IOException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			try (InputStream in = Files.newInputStream(file)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = in.read(buffer)) != -1) {
					md.update(buffer, 0, read);
				}
			}
			byte[] digest = md.digest();
			StringBuilder sb = new StringBuilder();
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new IOException("Failed to compute SHA-256 hash", e);
		}
	}

	/**
	 * Computes the SHA-256 hash of a byte array.
	 */
	public static String computeSha256(byte[] data) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(data);
			StringBuilder sb = new StringBuilder();
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			return Integer.toHexString(Arrays.hashCode(data));
		}
	}

	/**
	 * Validates a cosmetic ZIP package against security boundaries, file types, and SHA-256 hash.
	 *
	 * @param zipPath Path to the ZIP file on disk
	 * @param expectedHash Optional expected SHA-256 hash from catalogue metadata (null/blank to skip)
	 * @return ValidationResult indicating success or specific failure reason
	 */
	public static ValidationResult validatePackage(Path zipPath, String expectedHash) {
		if (!Files.exists(zipPath)) {
			return ValidationResult.failure("ZIP package file does not exist: " + zipPath);
		}

		try {
			long fileSize = Files.size(zipPath);
			if (fileSize > MAX_PACKAGE_FILE_SIZE) {
				return ValidationResult.failure("Package file size (" + fileSize + " bytes) exceeds maximum limit (" + MAX_PACKAGE_FILE_SIZE + " bytes)");
			}
			if (fileSize == 0) {
				return ValidationResult.failure("Package file is empty (0 bytes)");
			}

			// 1. Verify SHA-256 hash if provided
			if (expectedHash != null && !expectedHash.isBlank()) {
				String actualHash = computeSha256(zipPath);
				if (!actualHash.equalsIgnoreCase(expectedHash.trim())) {
					return ValidationResult.failure("SHA-256 hash mismatch! Expected: " + expectedHash + ", actual: " + actualHash);
				}
			}

			// 2. Validate ZIP internal entries and path safety
			boolean hasManifest = false;
			int entryCount = 0;
			long totalUncompressedBytes = 0;

			try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
				Enumeration<? extends ZipEntry> entries = zipFile.entries();
				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					entryCount++;

					if (entryCount > MAX_ENTRY_COUNT) {
						return ValidationResult.failure("ZIP contains too many entries (exceeds " + MAX_ENTRY_COUNT + ")");
					}

					String entryName = entry.getName();

					// ZIP slip check: reject illegal path traversal characters
					if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\") || entryName.contains(":") ) {
						return ValidationResult.failure("Malicious path detected in ZIP entry: " + entryName);
					}

					if (entry.isDirectory()) {
						continue;
					}

					// ZIP bomb check
					long entrySize = entry.getSize();
					if (entrySize > MAX_SINGLE_ENTRY_UNCOMPRESSED_SIZE) {
						return ValidationResult.failure("Entry '" + entryName + "' size exceeds limit (" + entrySize + " > " + MAX_SINGLE_ENTRY_UNCOMPRESSED_SIZE + ")");
					}
					if (entrySize > 0) {
						totalUncompressedBytes += entrySize;
						if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_SIZE) {
							return ValidationResult.failure("Total uncompressed size exceeds limit (" + MAX_TOTAL_UNCOMPRESSED_SIZE + " bytes)");
						}
					}

					// Extension check
					String ext = getFileExtension(entryName).toLowerCase(Locale.ROOT);
					if (!ALLOWED_EXTENSIONS.contains(ext)) {
						return ValidationResult.failure("Disallowed file type in ZIP entry '" + entryName + "': extension '." + ext + "' is not permitted");
					}

					if (entryName.equalsIgnoreCase("manifest.json") || entryName.endsWith("/manifest.json")) {
						hasManifest = true;
					}
				}
			}

			if (!hasManifest) {
				return ValidationResult.failure("Missing required 'manifest.json' in cosmetic package");
			}

			return ValidationResult.success();
		} catch (Exception e) {
			return ValidationResult.failure("Corrupted or invalid ZIP file: " + e.getMessage());
		}
	}

	public static String getFileExtension(String fileName) {
		int dotIndex = fileName.lastIndexOf('.');
		return (dotIndex == -1 || dotIndex == fileName.length() - 1) ? "" : fileName.substring(dotIndex + 1);
	}
}

