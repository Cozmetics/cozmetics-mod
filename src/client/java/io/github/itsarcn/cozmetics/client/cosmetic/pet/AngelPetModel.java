package io.github.itsarcn.cozmetics.client.cosmetic.pet;

import io.github.itsarcn.cozmetics.cosmetic.AngelPetCosmetic;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * GeckoLib GeoModel for the Angel Pet.
 */
public class AngelPetModel extends GeoModel<AngelPetAnimatable> {
	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		return AngelPetCosmetic.MODEL_PATH;
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		return AngelPetCosmetic.TEXTURE_PATH;
	}

	@Override
	public Identifier getAnimationResource(AngelPetAnimatable animatable) {
		return AngelPetCosmetic.ANIMATION_PATH;
	}
}

