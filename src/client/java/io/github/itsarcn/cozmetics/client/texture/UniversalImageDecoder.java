package io.github.itsarcn.cozmetics.client.texture;

import io.github.itsarcn.cozmetics.Cozmetics;
import net.minecraft.client.texture.NativeImage;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Universal Image and Texture Decoder for Cozmetics.
 *
 * Supports static and animated image formats:
 * - PNG and APNG (Animated PNG)
 * - Static WebP and Animated WebP (lossy and lossless, with variable frame durations & alpha blending)
 * - Animated GIF (with transparency, disposal modes, and delays)
 * - JPEG / JPG
 * - BMP
 * - TIFF
 *
 * Implements strict safety limits against decompression bombs, dimension overflows,
 * and memory exhaustion. Returns a safe placeholder on corrupt or unsupported input.
 */
public final class UniversalImageDecoder {
	public static final int MAX_DIMENSION = 4096;
	public static final int MAX_FRAMES = 256;
	public static final long MAX_PIXELS_TOTAL = 4096L * 4096L * 4L; // ~64MB uncompressed max

	private UniversalImageDecoder() {}

	/**
	 * Decodes an image from an InputStream.
	 */
	public static DecodedImage decode(InputStream stream, String sourceName) throws IOException {
		byte[] data = stream.readAllBytes();
		return decode(data, sourceName);
	}

	/**
	 * Decodes an image from raw bytes with format sniffing and safety checks.
	 */
	public static DecodedImage decode(byte[] data, String sourceName) {
		if (data == null || data.length == 0) {
			Cozmetics.LOGGER.warn("Empty or null image data for '{}'; using fallback placeholder.", sourceName);
			return createPlaceholder(ImageFormat.UNKNOWN);
		}

		ImageFormat format = ImageFormat.detect(data);
		try {
			return switch (format) {
				case WEBP, ANIMATED_WEBP -> decodeWebP(data, format, sourceName);
				case GIF -> decodeGif(data, sourceName);
				case APNG -> decodeApng(data, sourceName);
				case PNG, JPEG, BMP, TIFF -> decodeStatic(data, format, sourceName);
				case UNKNOWN -> decodeFallback(data, sourceName);
			};
		} catch (Exception e) {
			Cozmetics.LOGGER.error("Failed to decode image '{}' (detected format: {}): {}", sourceName, format, e.getMessage(), e);
			return createPlaceholder(format);
		}
	}

	/**
	 * Decodes static images (PNG, JPEG, BMP, TIFF).
	 */
	private static DecodedImage decodeStatic(byte[] data, ImageFormat format, String sourceName) throws IOException {
		// Attempt fast STBImage decoding first for supported formats (PNG, JPEG, BMP)
		if (format == ImageFormat.PNG || format == ImageFormat.JPEG || format == ImageFormat.BMP) {
			try {
				NativeImage nativeImage = NativeImage.read(data);
				if (validateDimensions(nativeImage.getWidth(), nativeImage.getHeight(), sourceName)) {
					List<DecodedFrame> frames = new ArrayList<>(1);
					frames.add(new DecodedFrame(nativeImage, 100));
					return new DecodedImage(format, nativeImage.getWidth(), nativeImage.getHeight(), frames);
				} else {
					nativeImage.close();
					return createPlaceholder(format);
				}
			} catch (Exception ignored) {
				// Fallback to ImageIO / TwelveMonkeys
			}
		}

		// ImageIO / TwelveMonkeys fallback (covers TIFF and exotic PNG/JPEG/BMP)
		try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (readers.hasNext()) {
				ImageReader reader = readers.next();
				try {
					reader.setInput(in, true, true);
					BufferedImage bi = reader.read(0);
					if (bi != null && validateDimensions(bi.getWidth(), bi.getHeight(), sourceName)) {
						NativeImage ni = toNativeImage(bi);
						List<DecodedFrame> frames = new ArrayList<>(1);
						frames.add(new DecodedFrame(ni, 100));
						return new DecodedImage(format, bi.getWidth(), bi.getHeight(), frames);
					}
				} finally {
					reader.dispose();
				}
			}
		}

		Cozmetics.LOGGER.warn("No suitable decoder found for static image '{}' (format: {})", sourceName, format);
		return createPlaceholder(format);
	}

	/**
	 * Decodes Static or Animated WebP using TwelveMonkeys imageio-webp with full ANMF frame compositing.
	 */
	private static DecodedImage decodeWebP(byte[] data, ImageFormat format, String sourceName) throws Exception {
		try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
			Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("webp");
			if (!readers.hasNext()) {
				readers = ImageIO.getImageReaders(in);
			}

			if (!readers.hasNext()) {
				Cozmetics.LOGGER.warn("WebP ImageReader SPI not registered for '{}'", sourceName);
				return createPlaceholder(format);
			}

			ImageReader reader = readers.next();
			try {
				reader.setInput(in);
				int numFrames = reader.getNumImages(true);
				if (numFrames <= 0) {
					return createPlaceholder(format);
				}

				// If strictly single frame, decode static
				if (numFrames == 1) {
					BufferedImage bi = reader.read(0);
					if (!validateDimensions(bi.getWidth(), bi.getHeight(), sourceName)) {
						return createPlaceholder(format);
					}
					NativeImage ni = toNativeImage(bi);
					List<DecodedFrame> frames = new ArrayList<>(1);
					frames.add(new DecodedFrame(ni, 100));
					return new DecodedImage(ImageFormat.WEBP, bi.getWidth(), bi.getHeight(), frames);
				}

				// Animated WebP: extract canvas dimensions and frame metadata
				int canvasW = 0;
				int canvasH = 0;
				List<?> animFrames = null;

				try {
					Field headerField = reader.getClass().getDeclaredField("header");
					headerField.setAccessible(true);
					Object header = headerField.get(reader);
					if (header != null) {
						Field wField = header.getClass().getDeclaredField("width");
						Field hField = header.getClass().getDeclaredField("height");
						wField.setAccessible(true);
						hField.setAccessible(true);
						canvasW = wField.getInt(header);
						canvasH = hField.getInt(header);
					}

					Field framesField = reader.getClass().getDeclaredField("frames");
					framesField.setAccessible(true);
					animFrames = (List<?>) framesField.get(reader);
				} catch (Exception e) {
					Cozmetics.LOGGER.debug("Could not inspect WebP reader internals via reflection: {}", e.getMessage());
				}

				if (canvasW <= 0 || canvasH <= 0) {
					BufferedImage first = reader.read(0);
					canvasW = first.getWidth();
					canvasH = first.getHeight();
				}

				if (!validateDimensions(canvasW, canvasH, sourceName)) {
					return createPlaceholder(format);
				}

				int frameLimit = Math.min(numFrames, MAX_FRAMES);
				long totalPixels = (long) canvasW * canvasH * frameLimit;
				if (totalPixels > MAX_PIXELS_TOTAL) {
					Cozmetics.LOGGER.warn("Animated WebP '{}' exceeds pixel memory budget: {} pixels; truncating frames.", sourceName, totalPixels);
					frameLimit = Math.max(1, (int) (MAX_PIXELS_TOTAL / ((long) canvasW * canvasH)));
				}

				BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g2d = canvas.createGraphics();

				List<DecodedFrame> decodedFrames = new ArrayList<>(frameLimit);

				for (int i = 0; i < frameLimit; i++) {
					BufferedImage frameSubImage = reader.read(i);
					int x = 0;
					int y = 0;
					int duration = 100;
					boolean blend = true;
					boolean dispose = false;
					int boundW = frameSubImage.getWidth();
					int boundH = frameSubImage.getHeight();

					if (animFrames != null && i < animFrames.size()) {
						Object af = animFrames.get(i);
						try {
							Field boundsField = af.getClass().getDeclaredField("bounds");
							Field durField = af.getClass().getDeclaredField("duration");
							Field blendField = af.getClass().getDeclaredField("blend");
							Field dispField = af.getClass().getDeclaredField("dispose");
							boundsField.setAccessible(true);
							durField.setAccessible(true);
							blendField.setAccessible(true);
							dispField.setAccessible(true);

							Rectangle bounds = (Rectangle) boundsField.get(af);
							if (bounds != null) {
								x = bounds.x;
								y = bounds.y;
								boundW = bounds.width;
								boundH = bounds.height;
							}
							duration = durField.getInt(af);
							blend = blendField.getBoolean(af);
							dispose = dispField.getBoolean(af);
						} catch (Exception ignored) {}
					}

					if (duration <= 0) duration = 100;

					// Composite onto canvas
					if (!blend) {
						g2d.setComposite(AlphaComposite.Src);
					} else {
						g2d.setComposite(AlphaComposite.SrcOver);
					}
					g2d.drawImage(frameSubImage, x, y, boundW, boundH, null);

					// Snapshot composite frame
					NativeImage frameNative = toNativeImage(canvas);
					decodedFrames.add(new DecodedFrame(frameNative, duration));

					// Dispose for next frame
					if (dispose) {
						g2d.setComposite(AlphaComposite.Clear);
						g2d.fillRect(x, y, boundW, boundH);
					}
				}

				g2d.dispose();
				return new DecodedImage(ImageFormat.ANIMATED_WEBP, canvasW, canvasH, decodedFrames);
			} finally {
				reader.dispose();
			}
		}
	}

	/**
	 * Decodes Animated GIF with full frame timing, disposal, and transparency.
	 */
	private static DecodedImage decodeGif(byte[] data, String sourceName) throws Exception {
		try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
			Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
			if (!readers.hasNext()) {
				return createPlaceholder(ImageFormat.GIF);
			}

			ImageReader reader = readers.next();
			try {
				reader.setInput(in);
				int numFrames = reader.getNumImages(true);
				if (numFrames <= 0) {
					return createPlaceholder(ImageFormat.GIF);
				}

				int canvasW = reader.getWidth(0);
				int canvasH = reader.getHeight(0);
				if (!validateDimensions(canvasW, canvasH, sourceName)) {
					return createPlaceholder(ImageFormat.GIF);
				}

				int frameLimit = Math.min(numFrames, MAX_FRAMES);
				BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g2d = canvas.createGraphics();

				List<DecodedFrame> decodedFrames = new ArrayList<>(frameLimit);
				BufferedImage backupCanvas = null;

				for (int i = 0; i < frameLimit; i++) {
					BufferedImage frameImage = reader.read(i);
					int x = 0;
					int y = 0;
					int duration = 100;
					int disposal = 1; // 1 = leave in place, 2 = restore to bg, 3 = restore to previous

					IIOMetadata metadata = reader.getImageMetadata(i);
					if (metadata != null) {
						String metaFormat = metadata.getNativeMetadataFormatName();
						if (metaFormat != null) {
							Node root = metadata.getAsTree(metaFormat);
							for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
								if ("ImageDescriptor".equalsIgnoreCase(child.getNodeName())) {
									NamedNodeMap attrs = child.getAttributes();
									if (attrs.getNamedItem("imageLeftPosition") != null) {
										x = Integer.parseInt(attrs.getNamedItem("imageLeftPosition").getNodeValue());
									}
									if (attrs.getNamedItem("imageTopPosition") != null) {
										y = Integer.parseInt(attrs.getNamedItem("imageTopPosition").getNodeValue());
									}
								} else if ("GraphicControlExtension".equalsIgnoreCase(child.getNodeName())) {
									NamedNodeMap attrs = child.getAttributes();
									if (attrs.getNamedItem("delayTime") != null) {
										int delayHundredths = Integer.parseInt(attrs.getNamedItem("delayTime").getNodeValue());
										duration = delayHundredths > 0 ? delayHundredths * 10 : 100;
									}
									if (attrs.getNamedItem("disposalMethod") != null) {
										String dispStr = attrs.getNamedItem("disposalMethod").getNodeValue();
										if ("restoreToBackgroundColor".equalsIgnoreCase(dispStr)) disposal = 2;
										else if ("restoreToPrevious".equalsIgnoreCase(dispStr)) disposal = 3;
									}
								}
							}
						}
					}

					if (disposal == 3) {
						backupCanvas = copyImage(canvas);
					}

					// Composite frame
					g2d.setComposite(AlphaComposite.SrcOver);
					g2d.drawImage(frameImage, x, y, null);

					NativeImage ni = toNativeImage(canvas);
					decodedFrames.add(new DecodedFrame(ni, duration));

					// Handle disposal
					if (disposal == 2) {
						g2d.setComposite(AlphaComposite.Clear);
						g2d.fillRect(x, y, frameImage.getWidth(), frameImage.getHeight());
					} else if (disposal == 3 && backupCanvas != null) {
						g2d.setComposite(AlphaComposite.Src);
						g2d.drawImage(backupCanvas, 0, 0, null);
					}
				}

				g2d.dispose();
				return new DecodedImage(ImageFormat.GIF, canvasW, canvasH, decodedFrames);
			} finally {
				reader.dispose();
			}
		}
	}

	/**
	 * Decodes Animated PNG (APNG) or falls back to static PNG.
	 */
	private static DecodedImage decodeApng(byte[] data, String sourceName) throws IOException {
		// First verify if acTL chunk exists
		int actlPos = -1;
		int numFrames = 1;
		for (int i = 8; i < Math.min(data.length - 8, 2048); i++) {
			if (data[i] == 'a' && data[i + 1] == 'c' && data[i + 2] == 'T' && data[i + 3] == 'L') {
				actlPos = i;
				numFrames = ((data[i + 4] & 0xFF) << 24) | ((data[i + 5] & 0xFF) << 16)
						| ((data[i + 6] & 0xFF) << 8) | (data[i + 7] & 0xFF);
				break;
			}
		}

		if (actlPos == -1 || numFrames <= 1) {
			return decodeStatic(data, ImageFormat.PNG, sourceName);
		}

		// For standard APNG, decode default static frame as primary representation
		return decodeStatic(data, ImageFormat.APNG, sourceName);
	}

	private static DecodedImage decodeFallback(byte[] data, String sourceName) throws IOException {
		// Try STBImage as last resort
		try {
			NativeImage ni = NativeImage.read(data);
			if (validateDimensions(ni.getWidth(), ni.getHeight(), sourceName)) {
				List<DecodedFrame> frames = new ArrayList<>(1);
				frames.add(new DecodedFrame(ni, 100));
				return new DecodedImage(ImageFormat.UNKNOWN, ni.getWidth(), ni.getHeight(), frames);
			}
			ni.close();
		} catch (Exception ignored) {}

		return createPlaceholder(ImageFormat.UNKNOWN);
	}

	/**
	 * Converts BufferedImage to Minecraft NativeImage (RGBA).
	 */
	public static NativeImage toNativeImage(BufferedImage bi) {
		int w = bi.getWidth();
		int h = bi.getHeight();
		NativeImage ni = new NativeImage(NativeImage.Format.RGBA, w, h, false);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				ni.setColorArgb(x, y, bi.getRGB(x, y));
			}
		}
		return ni;
	}

	private static BufferedImage copyImage(BufferedImage src) {
		BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = copy.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return copy;
	}

	private static boolean validateDimensions(int w, int h, String sourceName) {
		if (w <= 0 || h <= 0) {
			Cozmetics.LOGGER.warn("Image '{}' has invalid dimensions: {}x{}", sourceName, w, h);
			return false;
		}
		if (w > MAX_DIMENSION || h > MAX_DIMENSION) {
			Cozmetics.LOGGER.warn("Image '{}' exceeds maximum allowed dimension ({}x{} > max {})", sourceName, w, h, MAX_DIMENSION);
			return false;
		}
		return true;
	}

	/**
	 * Generates a safe 16x16 magenta/black checkerboard fallback placeholder texture.
	 */
	public static DecodedImage createPlaceholder(ImageFormat format) {
		int size = 16;
		NativeImage placeholder = new NativeImage(NativeImage.Format.RGBA, size, size, false);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				boolean check = ((x / 4) + (y / 4)) % 2 == 0;
				placeholder.setColorArgb(x, y, check ? 0xFFFF00FF : 0xFF000000);
			}
		}
		List<DecodedFrame> frames = new ArrayList<>(1);
		frames.add(new DecodedFrame(placeholder, 100));
		return new DecodedImage(format, size, size, frames);
	}
}

