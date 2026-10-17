package io.github.itsarcn.cozmetics.test;

import io.github.itsarcn.cozmetics.client.texture.DecodedFrame;
import io.github.itsarcn.cozmetics.client.texture.DecodedImage;
import io.github.itsarcn.cozmetics.client.texture.ImageFormat;
import io.github.itsarcn.cozmetics.client.texture.UniversalImageDecoder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UniversalImagePipelineTest {

	@Test
	void testFormatDetection() throws Exception {
		// 1. Static WebP
		byte[] angelBytes = Files.readAllBytes(new File("assets-desgins/angelPet/angelPet.webp").toPath());
		assertEquals(ImageFormat.WEBP, ImageFormat.detect(angelBytes));

		// 2. Animated WebP
		byte[] auraBytes = Files.readAllBytes(new File("assets-desgins/aura/beenTakenCorruptedAura.webp").toPath());
		assertEquals(ImageFormat.ANIMATED_WEBP, ImageFormat.detect(auraBytes));

		byte[] scytheBytes = Files.readAllBytes(new File("assets-desgins/back/medalScythe.webp").toPath());
		assertEquals(ImageFormat.ANIMATED_WEBP, ImageFormat.detect(scytheBytes));

		byte[] bootsBytes = Files.readAllBytes(new File("assets-desgins/boots/gamerRGBBoots.webp").toPath());
		assertEquals(ImageFormat.ANIMATED_WEBP, ImageFormat.detect(bootsBytes));

		// 3. PNG
		byte[] pngBytes = Files.readAllBytes(new File("src/main/resources/assets/cozmetics/textures/cosmetics/pets/angel_pet.png").toPath());
		assertEquals(ImageFormat.PNG, ImageFormat.detect(pngBytes));

		// 4. Corrupted / Unknown
		assertEquals(ImageFormat.UNKNOWN, ImageFormat.detect(new byte[]{0, 1, 2, 3}));
		assertEquals(ImageFormat.UNKNOWN, ImageFormat.detect(null));
	}

	@Test
	void testStaticWebPDecoding() throws Exception {
		byte[] bytes = Files.readAllBytes(new File("assets-desgins/angelPet/angelPet.webp").toPath());
		try (DecodedImage img = UniversalImageDecoder.decode(bytes, "angelPet.webp")) {
			assertNotNull(img);
			assertEquals(ImageFormat.WEBP, img.getFormat());
			assertEquals(64, img.getWidth());
			assertEquals(64, img.getHeight());
			assertEquals(1, img.getFrameCount());
			assertFalse(img.isAnimated());
			assertNotNull(img.getFrameAt(0).image());
		}
	}

	@Test
	void testAnimatedWebPDecodingAndCompositing() throws Exception {
		byte[] bytes = Files.readAllBytes(new File("assets-desgins/aura/beenTakenCorruptedAura.webp").toPath());
		try (DecodedImage img = UniversalImageDecoder.decode(bytes, "beenTakenCorruptedAura.webp")) {
			assertNotNull(img);
			assertEquals(ImageFormat.ANIMATED_WEBP, img.getFormat());
			assertEquals(64, img.getWidth());
			assertEquals(64, img.getHeight());
			assertEquals(3, img.getFrameCount());
			assertTrue(img.isAnimated());
			assertTrue(img.getTotalDurationMs() >= 300);

			// Test frame selection at variable times
			assertEquals(0, img.getFrameIndexAt(0));
			assertEquals(0, img.getFrameIndexAt(100));
			assertEquals(1, img.getFrameIndexAt(250));
			assertEquals(2, img.getFrameIndexAt(450));
			// Loop wrapping
			assertEquals(0, img.getFrameIndexAt(img.getTotalDurationMs()));
		}
	}

	@Test
	void testMultiFrameSuppliedAssets() throws Exception {
		// Test Medal Scythe (11 frames, 512x512)
		byte[] scytheBytes = Files.readAllBytes(new File("assets-desgins/back/medalScythe.webp").toPath());
		try (DecodedImage scythe = UniversalImageDecoder.decode(scytheBytes, "medalScythe.webp")) {
			assertEquals(ImageFormat.ANIMATED_WEBP, scythe.getFormat());
			assertEquals(512, scythe.getWidth());
			assertEquals(512, scythe.getHeight());
			assertEquals(11, scythe.getFrameCount());
		}

		// Test Boots (8 frames, 32x32)
		byte[] bootsBytes = Files.readAllBytes(new File("assets-desgins/boots/gamerRGBBoots.webp").toPath());
		try (DecodedImage boots = UniversalImageDecoder.decode(bootsBytes, "gamerRGBBoots.webp")) {
			assertEquals(ImageFormat.ANIMATED_WEBP, boots.getFormat());
			assertEquals(32, boots.getWidth());
			assertEquals(32, boots.getHeight());
			assertEquals(8, boots.getFrameCount());
		}

		// Test Fists (8 frames, 32x32)
		byte[] fistsBytes = Files.readAllBytes(new File("assets-desgins/fist/beenTakenCorruptedFists.webp").toPath());
		try (DecodedImage fists = UniversalImageDecoder.decode(fistsBytes, "beenTakenCorruptedFists.webp")) {
			assertEquals(ImageFormat.ANIMATED_WEBP, fists.getFormat());
			assertEquals(32, fists.getWidth());
			assertEquals(32, fists.getHeight());
			assertEquals(8, fists.getFrameCount());
		}
	}

	@Test
	void testErrorHandlingAndSafePlaceholder() {
		// Null or empty input
		try (DecodedImage img = UniversalImageDecoder.decode(new byte[0], "empty.png")) {
			assertNotNull(img);
			assertEquals(16, img.getWidth());
			assertEquals(16, img.getHeight());
			assertEquals(1, img.getFrameCount());
		}

		// Corrupted garbage data
		byte[] garbage = new byte[]{0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
		try (DecodedImage img = UniversalImageDecoder.decode(garbage, "corrupt.webp")) {
			assertNotNull(img);
			assertEquals(16, img.getWidth());
			assertEquals(16, img.getHeight());
		}
	}
}

