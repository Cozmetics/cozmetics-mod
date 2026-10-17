package io.github.itsarcn.cozmetics.cosmetic;

import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Common representation of a cosmetic item/model.
 * Prepared for static models, animated GeckoLib models, and future cosmetic types.
 */
public interface Cosmetic {
	/**
	 * Unique identifier for this cosmetic (e.g. cozmetics:angel_pet).
	 */
	Identifier getId();

	/**
	 * Category this cosmetic belongs to.
	 */
	CosmeticCategory getCategory();

	/**
	 * Human-readable display name.
	 */
	String getName();

	/**
	 * Whether this cosmetic uses an animated model (e.g. GeckoLib).
	 */
	default boolean isAnimated() {
		return getAnimationPath() != null;
	}

	/**
	 * Subpath/identifier for the model file.
	 */
	Identifier getModelPath();

	/**
	 * Identifier for the texture resource.
	 */
	Identifier getTexturePath();

	/**
	 * Subpath/identifier for the animation file (if animated, null otherwise).
	 */
	@Nullable
	Identifier getAnimationPath();

	/**
	 * Idle animation name to play if animated.
	 */
	@Nullable
	default String getIdleAnimationName() {
		return null;
	}

	/**
	 * Attachment slot where this cosmetic anchors on the player.
	 */
	default AttachmentSlot getAttachmentSlot() {
		return switch (getCategory()) {
			case PET -> AttachmentSlot.PET;
			case WINGS, BACK, AURA -> AttachmentSlot.BODY;
			case HEADWEAR, FACE -> AttachmentSlot.HEAD;
			case FISTS -> AttachmentSlot.ARMS;
			case BOOTS -> AttachmentSlot.LEGS;
			case CAPE -> AttachmentSlot.BODY;
		};
	}

	/**
	 * Additional transform options (translation, scale, static lighting).
	 */
	default AttachmentTransform getTransform() {
		return AttachmentTransform.DEFAULT;
	}
}
