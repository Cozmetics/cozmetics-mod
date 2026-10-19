package io.github.itsarcn.cozmetics.test;

import io.github.itsarcn.cozmetics.client.texture.DecodedImage;
import io.github.itsarcn.cozmetics.client.texture.ImageFormat;
import io.github.itsarcn.cozmetics.client.texture.UniversalImageDecoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UniversalImagePipelineTest {

	@Test
	void testFormatDetection() {
		// 1. Static WebP signature: RIFF....WEBPVP8
		byte[] staticWebp = new byte[]{
				'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
		};
		assertEquals(ImageFormat.WEBP, ImageFormat.detect(staticWebp));

		// 2. Animated WebP signature: RIFF....WEBPVP8X with animation flag bit (bit 1 of byte 20)
		byte[] animWebp = new byte[]{
				'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', 'X',
				10, 0, 0, 0, 0x02, 0, 0, 0
		};
		assertEquals(ImageFormat.ANIMATED_WEBP, ImageFormat.detect(animWebp));

		// 3. PNG signature
		byte[] pngBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
		assertEquals(ImageFormat.PNG, ImageFormat.detect(pngBytes));

		// 4. Corrupted / Unknown
		assertEquals(ImageFormat.UNKNOWN, ImageFormat.detect(new byte[]{0, 1, 2, 3}));
		assertEquals(ImageFormat.UNKNOWN, ImageFormat.detect(null));
	}

	@Test
	void testErrorHandlingAndSafePlaceholder() {
		// Null or empty input
		try (DecodedImage img = UniversalImageDecoder.decode(new byte[0], "empty.png")) {
			assertNotNull(img);
			assertEquals(16, img.getWidth());
			assertEquals(16, img.getHeight());
			assertEquals(1, img.getFrameCount());
			assertFalse(img.isAnimated());
			assertNotNull(img.getFrameAt(0).image());
		}

		// Corrupted garbage data
		byte[] garbage = new byte[]{0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
		try (DecodedImage img = UniversalImageDecoder.decode(garbage, "corrupt.webp")) {
			assertNotNull(img);
			assertEquals(16, img.getWidth());
			assertEquals(16, img.getHeight());
			assertEquals(1, img.getFrameCount());
			assertNotNull(img.getFrameAt(0).image());
		}
	}

	@Test
	void testRealCosmeticWebpDecoding() throws Exception {
		java.nio.file.Path webpFile = java.nio.file.Path.of("run/cozmetics/cache/extracted/cozmetics_demon_scythe/texture.webp");
		if (java.nio.file.Files.exists(webpFile)) {
			byte[] bytes = java.nio.file.Files.readAllBytes(webpFile);
			try (DecodedImage img = UniversalImageDecoder.decode(bytes, "demon_scythe.webp")) {
				assertNotNull(img);
				assertTrue(img.getWidth() > 0, "Width should be > 0: " + img.getWidth());
				assertTrue(img.getHeight() > 0, "Height should be > 0: " + img.getHeight());
				assertTrue(img.getFormat() == ImageFormat.WEBP || img.getFormat() == ImageFormat.ANIMATED_WEBP);
				assertTrue(img.getFrameCount() >= 1);
				assertNotNull(img.getFrameAt(0).image());
			}
		}
	}
}
