package io.github.itsarcn.cozmetics.cosmetic;

/**
 * Custom offset and rendering configuration for cosmetic attachments.
 *
 * @param translateY Custom vertical offset in Blockbench units (e.g. 3.3 for helmet-fitted crown).
 * @param scale Custom scale multiplier (default 1.0f).
 * @param staticLighting Whether full-bright / static lighting is requested.
 */
public record AttachmentTransform(float translateY, float scale, boolean staticLighting) {
	public static final AttachmentTransform DEFAULT = new AttachmentTransform(0.0f, 1.0f, false);

	public AttachmentTransform(float translateY) {
		this(translateY, 1.0f, false);
	}
}

