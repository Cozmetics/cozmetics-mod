package io.github.itsarcn.cozmetics.client.render.dynamic;

import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
//? if >1.21.4 {
import software.bernie.geckolib.renderer.base.GeoRenderState;
//?}

/**
 * Dynamic GeoModel providing model, texture, and animation identifiers from a Cosmetic definition.
 */
public class DynamicCosmeticModel extends GeoModel<DynamicCosmeticAnimatable> {
	private final Cosmetic cosmetic;

	public DynamicCosmeticModel(Cosmetic cosmetic) {
		this.cosmetic = cosmetic;
	}

	private Identifier resolveTexture() {
		Identifier baseTex = this.cosmetic.getTexturePath();

		// 1. If CosmeticTextureManager has a loaded texture, query frame at current time:
		var manager = io.github.itsarcn.cozmetics.client.texture.CosmeticTextureManager.getInstance();
		var entry = manager.getOrLoadTexture(baseTex);
		if (entry != null) {
			return entry.getFrameAt(System.currentTimeMillis());
		}

		// 2. If cosmetic definition specifies multi-file animated frames, cycle through them:
		if (this.cosmetic.getTextureFrameCount() > 1) {
			int frame = (int) ((System.currentTimeMillis() / this.cosmetic.getTextureFrameDurationMs()) % this.cosmetic.getTextureFrameCount());
			return this.cosmetic.getTexturePath(frame);
		}

		// 3. Otherwise return the base texture
		return baseTex;
	}

	//? if <=1.21.4 {
	/*@Override
	public Identifier getModelResource(DynamicCosmeticAnimatable animatable) {
		return this.cosmetic.getModelPath();
	}

	@Override
	public Identifier getTextureResource(DynamicCosmeticAnimatable animatable) {
		return resolveTexture();
	}*/
	//?} else {
	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		return this.cosmetic.getModelPath();
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		return resolveTexture();
	}
	//?}

	@Override
	public Identifier getAnimationResource(DynamicCosmeticAnimatable animatable) {
		return this.cosmetic.getAnimationPath();
	}
}
