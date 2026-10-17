package io.github.itsarcn.cozmetics.client.texture;

import net.minecraft.client.texture.NativeImage;

/**
 * A single decoded frame of a texture, containing the NativeImage pixel data
 * and display duration in milliseconds.
 */
public record DecodedFrame(NativeImage image, int durationMs) implements AutoCloseable {
	public DecodedFrame {
		if (durationMs <= 0) {
			durationMs = 100; // Standard 100ms fallback for frames with 0 duration
		}
	}

	@Override
	public void close() {
		if (image != null) {
			image.close();
		}
	}
}

