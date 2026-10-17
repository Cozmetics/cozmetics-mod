package io.github.itsarcn.cozmetics.client.texture;

import java.util.Arrays;

/**
 * Supported image formats detected via file signature (magic bytes).
 */
public enum ImageFormat {
	PNG("image/png", false),
	APNG("image/apng", true),
	JPEG("image/jpeg", false),
	WEBP("image/webp", false),
	ANIMATED_WEBP("image/webp", true),
	GIF("image/gif", true),
	BMP("image/bmp", false),
	TIFF("image/tiff", false),
	UNKNOWN("application/octet-stream", false);

	private final String mimeType;
	private final boolean animated;

	ImageFormat(String mimeType, boolean animated) {
		this.mimeType = mimeType;
		this.animated = animated;
	}

	public String getMimeType() {
		return mimeType;
	}

	public boolean isAnimated() {
		return animated;
	}

	/**
	 * Detects image format from raw bytes by analyzing header signatures.
	 */
	public static ImageFormat detect(byte[] data) {
		if (data == null || data.length < 12) {
			return UNKNOWN;
		}

		// 1. PNG / APNG signature: 89 50 4E 47 0D 0A 1A 0A
		if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G'
				&& data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A) {
			if (containsApngChunk(data)) {
				return APNG;
			}
			return PNG;
		}

		// 2. WebP signature: RIFF....WEBP
		if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
				&& data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
			if (isAnimatedWebP(data)) {
				return ANIMATED_WEBP;
			}
			return WEBP;
		}

		// 3. GIF signature: GIF87a or GIF89a
		if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8'
				&& (data[4] == '7' || data[4] == '9') && data[5] == 'a') {
			return GIF;
		}

		// 4. JPEG signature: FF D8 FF
		if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
			return JPEG;
		}

		// 5. BMP signature: 42 4D ('BM')
		if (data[0] == 'B' && data[1] == 'M') {
			return BMP;
		}

		// 6. TIFF signature: 49 49 2A 00 (little endian) or 4D 4D 00 2A (big endian)
		if ((data[0] == 0x49 && data[1] == 0x49 && data[2] == 0x2A && data[3] == 0x00)
				|| (data[0] == 0x4D && data[1] == 0x4D && data[2] == 0x00 && data[3] == 0x2A)) {
			return TIFF;
		}

		return UNKNOWN;
	}

	private static boolean isAnimatedWebP(byte[] data) {
		// In WebP extended header (VP8X), bit 1 of flags is animation.
		// Also scan for 'ANIM' or 'ANMF' chunk headers.
		if (data.length >= 21) {
			// Chunk header at offset 12
			if (data[12] == 'V' && data[13] == 'P' && data[14] == '8' && data[15] == 'X') {
				// Offset 20 is flags byte
				if ((data[20] & 0x02) != 0) {
					return true;
				}
			}
		}

		// Fallback scan first 512 bytes for "ANIM" or "ANMF"
		int limit = Math.min(data.length - 4, 512);
		for (int i = 12; i < limit; i++) {
			if (data[i] == 'A' && data[i + 1] == 'N' && data[i + 2] == 'I' && data[i + 3] == 'M') {
				return true;
			}
			if (data[i] == 'A' && data[i + 1] == 'N' && data[i + 2] == 'M' && data[i + 3] == 'F') {
				return true;
			}
		}
		return false;
	}

	private static boolean containsApngChunk(byte[] data) {
		// Scan chunks for 'acTL' before 'IDAT'
		int limit = Math.min(data.length - 4, 2048);
		for (int i = 8; i < limit; i++) {
			if (data[i] == 'a' && data[i + 1] == 'c' && data[i + 2] == 'T' && data[i + 3] == 'L') {
				return true;
			}
			if (data[i] == 'I' && data[i + 1] == 'D' && data[i + 2] == 'A' && data[i + 3] == 'T') {
				break;
			}
		}
		return false;
	}
}

