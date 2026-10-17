package io.github.itsarcn.cozmetics.test;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WebPAnimationCompositeTest {

	@Test
	void testCompositeAnimatedWebp() throws Exception {
		File file = new File("assets-desgins/aura/beenTakenCorruptedAura.webp");
		assertTrue(file.exists());

		try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			assertTrue(readers.hasNext(), "Should find WebP ImageReader");
			ImageReader reader = readers.next();
			reader.setInput(in);

			int numFrames = reader.getNumImages(true);
			System.out.println("Discovered frames: " + numFrames);
			assertTrue(numFrames >= 2, "Animated WebP should have at least 2 frames");

			// Inspect fields via reflection for canvas width/height and animation frames
			Field headerField = reader.getClass().getDeclaredField("header");
			headerField.setAccessible(true);
			Object header = headerField.get(reader);
			Field widthField = header.getClass().getDeclaredField("width");
			Field heightField = header.getClass().getDeclaredField("height");
			widthField.setAccessible(true);
			heightField.setAccessible(true);
			int canvasW = widthField.getInt(header);
			int canvasH = heightField.getInt(header);
			System.out.println("Canvas size: " + canvasW + "x" + canvasH);

			Field framesField = reader.getClass().getDeclaredField("frames");
			framesField.setAccessible(true);
			List<?> animFrames = (List<?>) framesField.get(reader);

			BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = canvas.createGraphics();

			for (int i = 0; i < numFrames; i++) {
				BufferedImage frameImg = reader.read(i);
				Object animFrame = animFrames.get(i);

				Field boundsField = animFrame.getClass().getDeclaredField("bounds");
				Field durationField = animFrame.getClass().getDeclaredField("duration");
				Field blendField = animFrame.getClass().getDeclaredField("blend");
				Field disposeField = animFrame.getClass().getDeclaredField("dispose");
				boundsField.setAccessible(true);
				durationField.setAccessible(true);
				blendField.setAccessible(true);
				disposeField.setAccessible(true);

				Rectangle bounds = (Rectangle) boundsField.get(animFrame);
				int duration = durationField.getInt(animFrame);
				boolean blend = blendField.getBoolean(animFrame);
				boolean dispose = disposeField.getBoolean(animFrame);

				System.out.printf("Frame %d: bounds=[%d,%d,%d,%d], duration=%dms, blend=%b, dispose=%b%n",
						i, bounds.x, bounds.y, bounds.width, bounds.height, duration, blend, dispose);

				// Draw onto canvas
				if (!blend) {
					g2d.setComposite(AlphaComposite.Src);
				} else {
					g2d.setComposite(AlphaComposite.SrcOver);
				}
				g2d.drawImage(frameImg, bounds.x, bounds.y, null);

				// Verify non-empty canvas
				assertNotNull(canvas);
				assertEquals(canvasW, canvas.getWidth());
				assertEquals(canvasH, canvas.getHeight());
			}
			g2d.dispose();
		}
	}
}

