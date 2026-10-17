package io.github.itsarcn.cozmetics.client.render.dynamic;

import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Dynamic GeoModel providing model, texture, and animation identifiers from a Cosmetic definition.
 */
public class DynamicCosmeticModel extends GeoModel<DynamicCosmeticAnimatable> {
	private final Cosmetic cosmetic;

	public DynamicCosmeticModel(Cosmetic cosmetic) {
		this.cosmetic = cosmetic;
	}

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		return this.cosmetic.getModelPath();
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		if (this.cosmetic.getTextureFrameCount() > 1) {
			int frame = (int) ((System.currentTimeMillis() / this.cosmetic.getTextureFrameDurationMs()) % this.cosmetic.getTextureFrameCount());
			return this.cosmetic.getTexturePath(frame);
		}
		return this.cosmetic.getTexturePath();
	}

	@Override
	public Identifier getAnimationResource(DynamicCosmeticAnimatable animatable) {
		return this.cosmetic.getAnimationPath();
	}
}

