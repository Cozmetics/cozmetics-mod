package io.github.itsarcn.cozmetics.client.cosmetic.pet;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * GeoObjectRenderer for the Angel Pet attached to the player.
 */
public class AngelPetRenderer extends GeoObjectRenderer<AngelPetAnimatable, PlayerEntityRenderState, GeoRenderState> {
	private final AngelPetAnimatable animatable = new AngelPetAnimatable();

	public AngelPetRenderer() {
		super(new AngelPetModel());
	}

	@Override
	public long getInstanceId(AngelPetAnimatable animatable, @Nullable PlayerEntityRenderState relatedObject) {
		return relatedObject != null ? (long) relatedObject.id : 0L;
	}

	@Override
	public void adjustRenderPose(RenderPassInfo<GeoRenderState> renderPassInfo) {
		// Minecraft feature rendering is in the entity space where player feet are at Y=1.5 and inverted.
		// The Blockbench model's anchor_body is pivoted at [0, 24, 0] (which is Y=1.5 in Minecraft blocks).
		// We translate to the player base and invert Y/X to match Blockbench Bedrock coordinate orientation.
		renderPassInfo.poseStack().translate(0.0F, 1.5F, 0.0F);
		renderPassInfo.poseStack().scale(-1.0F, -1.0F, 1.0F);
	}

	public void renderPet(MatrixStack matrices, OrderedRenderCommandQueue queue, @Nullable CameraRenderState camera, int light, PlayerEntityRenderState playerState) {
		performRenderPass(this.animatable, playerState, matrices, queue, camera, light, 0);
	}
}

