package io.github.itsarcn.cozmetics.cosmetic;

import net.minecraft.util.Identifier;

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
	boolean isAnimated();

	/**
	 * Subpath/identifier for the model file.
	 */
	Identifier getModelPath();

	/**
	 * Identifier for the texture resource.
	 */
	Identifier getTexturePath();

	/**
	 * Subpath/identifier for the animation file (if animated).
	 */
	Identifier getAnimationPath();
}

