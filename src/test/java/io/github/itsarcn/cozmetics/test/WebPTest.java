package io.github.itsarcn.cozmetics.test;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;

public class WebPTest {

	@Test
	void testWebPDecoding() throws Exception {
		File staticWebp = new File("assets-desgins/angelPet/angelPet.webp");
		File animWebp = new File("assets-desgins/aura/beenTakenCorruptedAura.webp");

		System.out.println("Static exists: " + staticWebp.exists());
		System.out.println("Anim exists: " + animWebp.exists());

		try (ImageInputStream in = ImageIO.createImageInputStream(staticWebp)) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (readers.hasNext()) {
				ImageReader reader = readers.next();
				reader.setInput(in);
				System.out.println("Reader for static: " + reader.getClass().getName());
				BufferedImage img = reader.read(0);
				System.out.println("Static decoded: " + img.getWidth() + "x" + img.getHeight());
			} else {
				System.out.println("No reader found for static webp");
			}
		}

		try (ImageInputStream in = ImageIO.createImageInputStream(animWebp)) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (readers.hasNext()) {
				ImageReader reader = readers.next();
				reader.setInput(in);
				System.out.println("Reader for anim: " + reader.getClass().getName());
				int numImages = reader.getNumImages(true);
				System.out.println("Anim num images: " + numImages);
				for (int i = 0; i < Math.min(numImages, 5); i++) {
					BufferedImage frame = reader.read(i);
					System.out.println("Frame " + i + ": " + frame.getWidth() + "x" + frame.getHeight());
				}
			} else {
				System.out.println("No reader found for anim webp");
			}
		}
	}
}

