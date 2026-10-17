package io.github.itsarcn.cozmetics.client.texture;

import java.util.Collections;
import java.util.List;

/**
 * Decoded image representation holding one or more frames with durations,
 * canvas dimensions, format information, and automatic time-based frame sampling.
 */
public class DecodedImage implements AutoCloseable {
	private final ImageFormat format;
	private final int width;
	private final int height;
	private final List<DecodedFrame> frames;
	private final int totalDurationMs;

	public DecodedImage(ImageFormat format, int width, int height, List<DecodedFrame> frames) {
		this.format = format;
		this.width = width;
		this.height = height;
		this.frames = Collections.unmodifiableList(frames);

		int total = 0;
		for (DecodedFrame frame : frames) {
			total += frame.durationMs();
		}
		this.totalDurationMs = Math.max(1, total);
	}

	public ImageFormat getFormat() {
		return format;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public List<DecodedFrame> getFrames() {
		return frames;
	}

	public int getFrameCount() {
		return frames.size();
	}

	public int getTotalDurationMs() {
		return totalDurationMs;
	}

	public boolean isAnimated() {
		return frames.size() > 1;
	}

	/**
	 * Computes the active frame index based on elapsed milliseconds, respecting
	 * variable per-frame durations.
	 */
	public int getFrameIndexAt(long elapsedMs) {
		if (frames.size() <= 1) {
			return 0;
		}
		long modulo = Math.abs(elapsedMs) % totalDurationMs;
		long accum = 0;
		for (int i = 0; i < frames.size(); i++) {
			accum += frames.get(i).durationMs();
			if (modulo < accum) {
				return i;
			}
		}
		return 0;
	}

	public DecodedFrame getFrameAt(long elapsedMs) {
		return frames.get(getFrameIndexAt(elapsedMs));
	}

	@Override
	public void close() {
		for (DecodedFrame frame : frames) {
			frame.close();
		}
	}
}

