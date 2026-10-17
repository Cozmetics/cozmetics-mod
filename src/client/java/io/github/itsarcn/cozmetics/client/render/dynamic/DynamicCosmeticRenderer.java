package io.github.itsarcn.cozmetics.client.render.dynamic;

import io.github.itsarcn.cozmetics.cosmetic.AttachmentSlot;
import io.github.itsarcn.cozmetics.cosmetic.AttachmentTransform;
import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * Reusable GeoObjectRenderer for rendering cosmetics attached to player entities.
 * Automatically handles anchor bone rotation matching player model parts,
 * custom attachment transforms, and backface-culled cutout rendering.
 */
public class DynamicCosmeticRenderer extends GeoObjectRenderer<DynamicCosmeticAnimatable, PlayerEntityRenderState, GeoRenderState> {
	private final Cosmetic cosmetic;
	private final DynamicCosmeticAnimatable animatable;

	public DynamicCosmeticRenderer(Cosmetic cosmetic) {
		super(new DynamicCosmeticModel(cosmetic));
		this.cosmetic = cosmetic;
		this.animatable = new DynamicCosmeticAnimatable(cosmetic);
	}

	@Override
	public long getInstanceId(DynamicCosmeticAnimatable animatable, @Nullable PlayerEntityRenderState relatedObject) {
		return relatedObject != null ? (long) relatedObject.id : 0L;
	}

	@Override
	public @Nullable RenderLayer getRenderType(GeoRenderState renderState, Identifier texture) {
		// Use entityCutout (with backface culling) to prevent coplanar Z-fighting artifacts on 2D quads
		return RenderLayers.entityCutout(texture);
	}

	@Override
	public void adjustRenderPose(RenderPassInfo<GeoRenderState> renderPassInfo) {
		AttachmentTransform transform = this.cosmetic.getTransform();
		var poseStack = renderPassInfo.poseStack();

		// Feature rendering space: base of player model.
		// Translate to player origin + custom vertical offset
		poseStack.translate(0.0F, 1.5F + (transform.translateY() / 16.0F), 0.0F);

		// Apply scale if specified
		if (transform.scale() != 1.0F) {
			poseStack.scale(transform.scale(), transform.scale(), transform.scale());
		}

		// Invert Y and X to match Bedrock coordinates
		poseStack.scale(-1.0F, -1.0F, 1.0F);
	}

	public void renderCosmetic(
			MatrixStack matrices,
			OrderedRenderCommandQueue queue,
			@Nullable CameraRenderState camera,
			int light,
			PlayerEntityRenderState playerState,
			PlayerEntityModel playerModel
	) {
		int effectiveLight = this.cosmetic.getTransform().staticLighting() ? 0x00F000F0 : light;

		RenderPassInfo.BoneUpdater<GeoRenderState> boneUpdater = (passInfo, snapshots) -> {
			// Synchronize anchor bones with player model rotations
			snapshots.ifPresent("anchor_head", s -> s.setRotation(-playerModel.head.pitch, -playerModel.head.yaw, playerModel.head.roll));

			snapshots.ifPresent("anchor_body", s -> {
				// Pets stay upright and hover beside the player rather than tilting with torso
				if (this.cosmetic.getAttachmentSlot() != AttachmentSlot.PET) {
					s.setRotation(-playerModel.body.pitch, -playerModel.body.yaw, playerModel.body.roll);
				}
			});

			snapshots.ifPresent("anchor_leftarm", s -> s.setRotation(-playerModel.leftArm.pitch, -playerModel.leftArm.yaw, playerModel.leftArm.roll));
			snapshots.ifPresent("anchor_rightarm", s -> s.setRotation(-playerModel.rightArm.pitch, -playerModel.rightArm.yaw, playerModel.rightArm.roll));

			snapshots.ifPresent("anchor_leftleg", s -> s.setRotation(-playerModel.leftLeg.pitch, -playerModel.leftLeg.yaw, playerModel.leftLeg.roll));
			snapshots.ifPresent("anchor_rightleg", s -> s.setRotation(-playerModel.rightLeg.pitch, -playerModel.rightLeg.yaw, playerModel.rightLeg.roll));
		};

		performRenderPass(this.animatable, playerState, matrices, queue, camera, effectiveLight, 0, boneUpdater);
	}
}

