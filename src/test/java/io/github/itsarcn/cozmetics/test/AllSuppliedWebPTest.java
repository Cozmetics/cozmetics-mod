package io.github.itsarcn.cozmetics.test;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

public class AllSuppliedWebPTest {

	@Test
	void testAllSuppliedWebP() throws Exception {
		String[] files = {
				"assets-desgins/angelPet/angelPet.webp",
				"assets-desgins/angelPet/texture.webp",
				"assets-desgins/aura/beenTakenCorruptedAura.webp",
				"assets-desgins/back/medalScythe.webp",
				"assets-desgins/boots/gamerRGBBoots.webp",
				"assets-desgins/faces/demon2024CrowMask.webp",
				"assets-desgins/fist/beenTakenCorruptedFists.webp",
				"assets-desgins/hats/dontTouchMyLifeBlackCrown.webp",
				"assets-desgins/wings/halloween2024BatWingsV2.webp"
		};

		for (String path : files) {
			File f = new File(path);
			assertTrue(f.exists(), "File should exist: " + path);
			try (ImageInputStream in = ImageIO.createImageInputStream(f)) {
				Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
				assertTrue(readers.hasNext(), "Reader should be found for: " + path);
				ImageReader reader = readers.next();
				reader.setInput(in);
				int numImages = reader.getNumImages(true);
				BufferedImage first = reader.read(0);
				assertNotNull(first, "First frame should not be null for " + path);
				System.out.printf("Decoded %s: %dx%d, %d frame(s)%n",
						f.getName(), first.getWidth(), first.getHeight(), numImages);
			}
		}
	}
}

