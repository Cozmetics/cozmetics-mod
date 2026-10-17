package io.github.itsarcn.cozmetics.cosmetic;

import io.github.itsarcn.cozmetics.Cozmetics;
import net.minecraft.util.Identifier;

/**
 * Definition of the first working cosmetic: Angel Pet.
 */
public class AngelPetCosmetic implements Cosmetic {
	public static final Identifier ID = Cozmetics.id("angel_pet");
	public static final Identifier MODEL_PATH = Cozmetics.id("cosmetics/pets/angel_pet");
	public static final Identifier TEXTURE_PATH = Cozmetics.id("textures/cosmetics/pets/angel_pet.png");
	public static final Identifier ANIMATION_PATH = Cozmetics.id("cosmetics/pets/angel_pet");

	@Override
	public Identifier getId() {
		return ID;
	}

	@Override
	public CosmeticCategory getCategory() {
		return CosmeticCategory.PET;
	}

	@Override
	public String getName() {
		return "Angel Pet";
	}

	@Override
	public boolean isAnimated() {
		return true;
	}

	@Override
	public Identifier getModelPath() {
		return MODEL_PATH;
	}

	@Override
	public Identifier getTexturePath() {
		return TEXTURE_PATH;
	}

	@Override
	public Identifier getAnimationPath() {
		return ANIMATION_PATH;
	}
}

