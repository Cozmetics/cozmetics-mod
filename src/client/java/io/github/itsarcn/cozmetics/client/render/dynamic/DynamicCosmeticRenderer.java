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
 * Synchronizes model anchor bones with player limbs (head, body, arms, legs),
 * crouch/sneak translations, and custom attachment transforms.
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

	private static final software.bernie.geckolib.constant.dataticket.DataTicket<Boolean> IS_ISOLATED =
			software.bernie.geckolib.constant.dataticket.DataTicket.create("cozmetics_isolated", Boolean.class);

	@Override
	public void addRenderData(DynamicCosmeticAnimatable animatable, @Nullable PlayerEntityRenderState relatedObject, GeoRenderState renderState, float partialTick) {
		// Drive animation playback using continuous real-time so cosmetics animate in preview menus,
		// when singleplayer is paused, and in-game without hitching.
		double realTimeTick = (System.currentTimeMillis() % 100000000L) / 50.0;
		renderState.addGeckolibData(software.bernie.geckolib.constant.DataTickets.TICK, realTimeTick);

		boolean isIsolated = relatedObject instanceof io.github.itsarcn.cozmetics.client.render.IsolatedCosmeticRenderState;
		renderState.addGeckolibData(IS_ISOLATED, isIsolated);
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
		boolean isIsolated = Boolean.TRUE.equals(renderPassInfo.getGeckolibData(IS_ISOLATED));

		if (isIsolated) {
			// Center the cosmetic in isolated sidebar preview depending on its slot
			float centerY = switch (this.cosmetic.getAttachmentSlot()) {
				case HEAD -> 0.4F;
				case LEGS -> 1.2F;
				case BODY -> 0.85F;
				case ARMS -> 0.9F;
				case PET -> 1.1F;
			};
			poseStack.translate(0.0F, centerY, 0.0F);
		} else {
			// Feature rendering space: base of player model.
			// Translate to player origin (feet to neck = 1.5 blocks)
			poseStack.translate(0.0F, 1.5F, 0.0F);
		}

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
		float customOffsetY = this.cosmetic.getTransform().translateY();

		boolean isIsolated = playerState instanceof io.github.itsarcn.cozmetics.client.render.IsolatedCosmeticRenderState;

		RenderPassInfo.BoneUpdater<GeoRenderState> boneUpdater = (passInfo, snapshots) -> {
			// 1. Head (crowns, face masks)
			snapshots.ifPresent("anchor_head", s -> {
				if (isIsolated) {
					s.setRotation(0, 0, 0);
					s.setTranslation(0, 0, 0);
				} else {
					s.setRotation(
							-playerModel.head.pitch - s.getBone().baseRotX(),
							-playerModel.head.yaw - s.getBone().baseRotY(),
							playerModel.head.roll - s.getBone().baseRotZ()
					);
					s.setTranslation(
							playerModel.head.originX,
							playerModel.head.originY - customOffsetY,
							playerModel.head.originZ
					);
				}
			});

			// 2. Torso (wings, scythe, aura)
			snapshots.ifPresent("anchor_body", s -> {
				if (isIsolated) {
					s.setRotation(0, 0, 0);
					s.setTranslation(0, 0, 0);
				} else if (this.cosmetic.getAttachmentSlot() != AttachmentSlot.PET) {
					s.setRotation(
							-playerModel.body.pitch - s.getBone().baseRotX(),
							-playerModel.body.yaw - s.getBone().baseRotY(),
							playerModel.body.roll - s.getBone().baseRotZ()
					);
					if (playerModel.body.originY != 0.0F || playerModel.body.originZ != 0.0F) {
						s.setTranslation(playerModel.body.originX, playerModel.body.originY, playerModel.body.originZ);
					}
				}
			});

			// 3. Arms (fists, gauntlets) - synchronized with arm swings and sneaking
			snapshots.ifPresent("anchor_leftarm", s -> {
				if (isIsolated) {
					s.setRotation(0, 0, 0);
					s.setTranslation(0, 0, 0);
				} else {
					s.setRotation(
							-playerModel.leftArm.pitch - s.getBone().baseRotX(),
							-playerModel.leftArm.yaw - s.getBone().baseRotY(),
							playerModel.leftArm.roll - s.getBone().baseRotZ()
					);
					s.setTranslation(
							playerModel.leftArm.originX - 5.0F,
							playerModel.leftArm.originY - 2.0F,
							playerModel.leftArm.originZ
					);
				}
			});
			snapshots.ifPresent("anchor_rightarm", s -> {
				if (isIsolated) {
					s.setRotation(0, 0, 0);
					s.setTranslation(0, 0, 0);
				} else {
					s.setRotation(
							-playerModel.rightArm.pitch - s.getBone().baseRotX(),
							-playerModel.rightArm.yaw - s.getBone().baseRotY(),
							playerModel.rightArm.roll - s.getBone().baseRotZ()
					);
					s.setTranslation(
							playerModel.rightArm.originX + 5.0F,
							playerModel.rightArm.originY - 2.0F,
							playerModel.rightArm.originZ
					);
				}
			});

			// 4. Legs (boots) - synchronized with walking swing and crouching
			snapshots.ifPresent("anchor_leftleg", s -> {
				if (isIsolated) {
					s.setRotation(0, 0, 0);
					s.setTranslation(0, 0, 0);
				} else {
					s.setRotation(
							-playerModel.leftLeg.pitch - s.getBone().baseRotX(),
							-playerModel.leftLeg.yaw - s.getBone().baseRotY(),
							playerModel.leftLeg.roll - s.getBone().baseRotZ()
					);
					s.setTranslation(
							playerModel.leftLeg.originX - 1.9F,
							playerModel.leftLeg.originY - 12.0F,
							playerModel.leftLeg.originZ
					);
				}
			});
			snapshots.ifPresent("anchor_rightleg", s -> {
				if (isIsolated) {
					s.setRotation(0, 0, 0);
					s.setTranslation(0, 0, 0);
				} else {
					s.setRotation(
							-playerModel.rightLeg.pitch - s.getBone().baseRotX(),
							-playerModel.rightLeg.yaw - s.getBone().baseRotY(),
							playerModel.rightLeg.roll - s.getBone().baseRotZ()
					);
					s.setTranslation(
							playerModel.rightLeg.originX + 1.9F,
							playerModel.rightLeg.originY - 12.0F,
							playerModel.rightLeg.originZ
					);
				}
			});
		};

		performRenderPass(this.animatable, playerState, matrices, queue, camera, effectiveLight, 0, boneUpdater);
	}
}
