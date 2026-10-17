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
		Identifier baseTex = this.cosmetic.getTexturePath();

		// 1. If CosmeticTextureManager has an animated texture (e.g. animated WebP or GIF with >1 frames), use it:
		var manager = io.github.itsarcn.cozmetics.client.texture.CosmeticTextureManager.getInstance();
		var entry = manager.getOrLoadTexture(baseTex);
		if (entry != null && entry.isAnimated()) {
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

	@Override
	public Identifier getAnimationResource(DynamicCosmeticAnimatable animatable) {
		return this.cosmetic.getAnimationPath();
	}
}

