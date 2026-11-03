package io.github.itsarcn.cozmetics.client.render.dynamic;

import io.github.itsarcn.cozmetics.cosmetic.AttachmentSlot;
import io.github.itsarcn.cozmetics.cosmetic.AttachmentTransform;
import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

//? if >1.21.4 {
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;
//?}

//? if =1.21.11 {
import software.bernie.geckolib.renderer.base.RenderPassInfo;
//?} else if <=1.21.10 && >=1.21.5 {
/*import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.base.RenderModelPositioner;*/
//?} else if <=1.21.4 {
/*import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;*/
//?}

/**
 * Reusable GeoObjectRenderer for rendering cosmetics attached to player entities.
 * Synchronizes model anchor bones with player limbs (head, body, arms, legs),
 * crouch/sneak translations, and custom attachment transforms.
 */
//? if <=1.21.4 {
/*public class DynamicCosmeticRenderer extends GeoObjectRenderer<DynamicCosmeticAnimatable> {
	private final Cosmetic cosmetic;
	private final DynamicCosmeticAnimatable animatable;

	public DynamicCosmeticRenderer(Cosmetic cosmetic) {
		super(new DynamicCosmeticModel(cosmetic));
		this.cosmetic = cosmetic;
		this.animatable = new DynamicCosmeticAnimatable(cosmetic);
	}

	@Override
	public void preRender(MatrixStack poseStack, DynamicCosmeticAnimatable animatable, BakedGeoModel model, @Nullable VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		this.objectRenderTranslations = new org.joml.Matrix4f(poseStack.peek().getPositionMatrix());
		scaleModelForRender(this.scaleWidth, this.scaleHeight, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
	}

	public void renderCosmetic(
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			@Nullable Object camera,
			int light,
			@Nullable AbstractClientPlayerEntity player,
			@Nullable PlayerEntityModel<?> playerModel
	) {
		int effectiveLight = this.cosmetic.getTransform().staticLighting() ? 0x00F000F0 : light;
		float customOffsetY = this.cosmetic.getTransform().translateY();
		BakedGeoModel bakedModel = getGeoModel().getBakedModel(getGeoModel().getModelResource(this.animatable));

		if (bakedModel != null) {
			if (playerModel != null) {
				bakedModel.getBone("anchor_head").ifPresent(b -> {
					b.setRotX(-playerModel.head.pitch);
					b.setRotY(-playerModel.head.yaw);
					b.setRotZ(playerModel.head.roll);
					b.setPosX(playerModel.head.pivotX);
					b.setPosY(playerModel.head.pivotY - customOffsetY);
					b.setPosZ(playerModel.head.pivotZ);
				});
				bakedModel.getBone("anchor_body").ifPresent(b -> {
					if (this.cosmetic.getAttachmentSlot() != AttachmentSlot.PET && this.cosmetic.getCategory() != CosmeticCategory.PET) {
						b.setRotX(-playerModel.body.pitch);
						b.setRotY(-playerModel.body.yaw);
						b.setRotZ(playerModel.body.roll);
						b.setPosX(playerModel.body.pivotX);
						b.setPosY(playerModel.body.pivotY - customOffsetY);
						b.setPosZ(playerModel.body.pivotZ);
					} else if (playerModel.body.pivotY != 0.0F || playerModel.body.pivotZ != 0.0F) {
						b.setPosX(0);
						b.setPosY(playerModel.body.pivotY - customOffsetY);
						b.setPosZ(playerModel.body.pivotZ);
					}
				});
				bakedModel.getBone("anchor_leftarm").ifPresent(b -> {
					b.setRotX(-playerModel.leftArm.pitch);
					b.setRotY(-playerModel.leftArm.yaw);
					b.setRotZ(playerModel.leftArm.roll);
					b.setPosX(playerModel.leftArm.pivotX - 5.0F);
					b.setPosY(playerModel.leftArm.pivotY - 2.0F);
					b.setPosZ(playerModel.leftArm.pivotZ);
				});
				bakedModel.getBone("anchor_rightarm").ifPresent(b -> {
					b.setRotX(-playerModel.rightArm.pitch);
					b.setRotY(-playerModel.rightArm.yaw);
					b.setRotZ(playerModel.rightArm.roll);
					b.setPosX(playerModel.rightArm.pivotX + 5.0F);
					b.setPosY(playerModel.rightArm.pivotY - 2.0F);
					b.setPosZ(playerModel.rightArm.pivotZ);
				});
				bakedModel.getBone("anchor_leftleg").ifPresent(b -> {
					b.setRotX(-playerModel.leftLeg.pitch);
					b.setRotY(-playerModel.leftLeg.yaw);
					b.setRotZ(playerModel.leftLeg.roll);
					b.setPosX(playerModel.leftLeg.pivotX - 1.9F);
					b.setPosY(playerModel.leftLeg.pivotY - 12.0F);
					b.setPosZ(playerModel.leftLeg.pivotZ);
				});
				bakedModel.getBone("anchor_rightleg").ifPresent(b -> {
					b.setRotX(-playerModel.rightLeg.pitch);
					b.setRotY(-playerModel.rightLeg.yaw);
					b.setRotZ(playerModel.rightLeg.roll);
					b.setPosX(playerModel.rightLeg.pivotX + 1.9F);
					b.setPosY(playerModel.rightLeg.pivotY - 12.0F);
					b.setPosZ(playerModel.rightLeg.pivotZ);
				});
			} else {
				for (String anchorName : new String[]{"anchor_head", "anchor_body", "anchor_leftarm", "anchor_rightarm", "anchor_leftleg", "anchor_rightleg"}) {
					bakedModel.getBone(anchorName).ifPresent(b -> {
						b.setRotX(0); b.setRotY(0); b.setRotZ(0);
						b.setPosX(0); b.setPosY(0); b.setPosZ(0);
					});
				}
			}
		}

		matrices.push();
		if (playerModel == null) {
			matrices.scale(-1.0F, -1.0F, 1.0F);
			if (this.cosmetic.getTransform().scale() != 1.0F) {
				float s = this.cosmetic.getTransform().scale();
				matrices.scale(s, s, s);
			}
			io.github.itsarcn.cozmetics.client.render.CosmeticModelBounds bounds =
					io.github.itsarcn.cozmetics.client.render.CosmeticModelBounds.get(this.cosmetic);
			matrices.translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());
		} else {
			matrices.translate(0.0F, 1.5F, 0.0F);
			if (this.cosmetic.getTransform().scale() != 1.0F) {
				float s = this.cosmetic.getTransform().scale();
				matrices.scale(s, s, s);
			}
			matrices.scale(-1.0F, -1.0F, 1.0F);
		}

		Identifier activeTex = getGeoModel().getTextureResource(this.animatable);
		RenderLayer renderType = RenderLayer.getEntityCutoutNoCull(activeTex != null ? activeTex : this.cosmetic.getTexturePath());
		render(matrices, this.animatable, vertexConsumers, renderType, null, effectiveLight, 0);
		matrices.pop();
	}
}
*///?} else {
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
		if (relatedObject instanceof io.github.itsarcn.cozmetics.client.render.IsolatedCosmeticRenderState iso) {
			return (long) iso.id;
		}
		return relatedObject != null ? (long) relatedObject.id : 0L;
	}

	private static final software.bernie.geckolib.constant.dataticket.DataTicket<Boolean> IS_ISOLATED =
			software.bernie.geckolib.constant.dataticket.DataTicket.create("cozmetics_isolated", Boolean.class);

	@Override
	public void addRenderData(DynamicCosmeticAnimatable animatable, @Nullable PlayerEntityRenderState relatedObject, GeoRenderState renderState, float partialTick) {
		boolean isThumbnail = (relatedObject instanceof io.github.itsarcn.cozmetics.client.render.IsolatedCosmeticRenderState iso && iso.isThumbnail());
		// Drive animation playback using continuous real-time for live previews and in-game, or freeze at 0 for static thumbnails
		double realTimeTick = isThumbnail ? 0.0 : (System.currentTimeMillis() % 100000000L) / 50.0;
		renderState.addGeckolibData(software.bernie.geckolib.constant.DataTickets.TICK, realTimeTick);

		boolean isIsolated = relatedObject instanceof io.github.itsarcn.cozmetics.client.render.IsolatedCosmeticRenderState;
		renderState.addGeckolibData(IS_ISOLATED, isIsolated);
	}

	@Override
	public @Nullable RenderLayer getRenderType(GeoRenderState renderState, Identifier texture) {
		// Disable backface culling so flat 2D quads (e.g. scythe blades, wings, thin geometries) are visible from both sides
		//? if >=1.21.11 {
		return RenderLayers.entityCutoutNoCull(texture);
		//?} else {
		/*return RenderLayer.getEntityCutoutNoCull(texture);*/
		//?}
	}

	//? if =1.21.11 {
	@Override
	public void adjustRenderPose(RenderPassInfo<GeoRenderState> renderPassInfo) {
		AttachmentTransform transform = this.cosmetic.getTransform();
		var poseStack = renderPassInfo.poseStack();
		boolean isIsolated = Boolean.TRUE.equals(renderPassInfo.getGeckolibData(IS_ISOLATED));

		if (isIsolated) {
			// Center the cosmetic at (0, 0, 0) dynamically using computed model bounds.
			// Formula cancels out LivingEntityRenderer's -1.501F offset and aligns geometric center to pivot.
			io.github.itsarcn.cozmetics.client.render.CosmeticModelBounds bounds =
					io.github.itsarcn.cozmetics.client.render.CosmeticModelBounds.get(this.cosmetic);
			float tx = bounds.centerX();
			float ty = 1.501F + bounds.centerY();
			float tz = -bounds.centerZ();

			poseStack.translate(tx, ty, tz);
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
	//?} else if <=1.21.10 && >=1.21.5 {
	/*@Override
	public void adjustRenderPose(GeoRenderState renderState, MatrixStack poseStack, BakedGeoModel model, CameraRenderState cameraState) {
		AttachmentTransform transform = this.cosmetic.getTransform();
		boolean isIsolated = Boolean.TRUE.equals(renderState.getGeckolibData(IS_ISOLATED));

		if (isIsolated) {
			io.github.itsarcn.cozmetics.client.render.CosmeticModelBounds bounds =
					io.github.itsarcn.cozmetics.client.render.CosmeticModelBounds.get(this.cosmetic);
			float tx = bounds.centerX();
			float ty = 1.501F + bounds.centerY();
			float tz = -bounds.centerZ();

			poseStack.translate(tx, ty, tz);
		} else {
			poseStack.translate(0.0F, 1.5F, 0.0F);
		}

		if (transform.scale() != 1.0F) {
			poseStack.scale(transform.scale(), transform.scale(), transform.scale());
		}

		poseStack.scale(-1.0F, -1.0F, 1.0F);
	}*///?}

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

		//? if =1.21.11 {
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
				} else if (this.cosmetic.getAttachmentSlot() != AttachmentSlot.PET && this.cosmetic.getCategory() != CosmeticCategory.PET) {
					s.setRotation(
							-playerModel.body.pitch - s.getBone().baseRotX(),
							-playerModel.body.yaw - s.getBone().baseRotY(),
							playerModel.body.roll - s.getBone().baseRotZ()
					);
					s.setTranslation(
							playerModel.body.originX,
							playerModel.body.originY - customOffsetY,
							playerModel.body.originZ
					);
				} else {
					// Pets don't tilt with body pitch/roll, but track sneaking/crouching offset
					if (playerModel.body.originY != 0.0F || playerModel.body.originZ != 0.0F) {
						s.setTranslation(0, playerModel.body.originY - customOffsetY, playerModel.body.originZ);
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
		//?} else if <=1.21.10 && >=1.21.5 {
		/*RenderModelPositioner<GeoRenderState> positioner = RenderModelPositioner.add(null, (renderState, bakedModel) -> {
			bakedModel.getBone("anchor_head").ifPresent(b -> {
				if (isIsolated) {
					b.setRotX(0); b.setRotY(0); b.setRotZ(0);
					b.setPosX(0); b.setPosY(0); b.setPosZ(0);
				} else {
					b.setRotX(-playerModel.head.pitch);
					b.setRotY(-playerModel.head.yaw);
					b.setRotZ(playerModel.head.roll);
					b.setPosX(playerModel.head.originX);
					b.setPosY(playerModel.head.originY - customOffsetY);
					b.setPosZ(playerModel.head.originZ);
				}
			});
			bakedModel.getBone("anchor_body").ifPresent(b -> {
				if (isIsolated) {
					b.setRotX(0); b.setRotY(0); b.setRotZ(0);
					b.setPosX(0); b.setPosY(0); b.setPosZ(0);
				} else if (this.cosmetic.getAttachmentSlot() != AttachmentSlot.PET && this.cosmetic.getCategory() != CosmeticCategory.PET) {
					b.setRotX(-playerModel.body.pitch);
					b.setRotY(-playerModel.body.yaw);
					b.setRotZ(playerModel.body.roll);
					b.setPosX(playerModel.body.originX);
					b.setPosY(playerModel.body.originY - customOffsetY);
					b.setPosZ(playerModel.body.originZ);
				} else {
					if (playerModel.body.originY != 0.0F || playerModel.body.originZ != 0.0F) {
						b.setPosX(0);
						b.setPosY(playerModel.body.originY - customOffsetY);
						b.setPosZ(playerModel.body.originZ);
					}
				}
			});
			bakedModel.getBone("anchor_leftarm").ifPresent(b -> {
				if (isIsolated) {
					b.setRotX(0); b.setRotY(0); b.setRotZ(0);
					b.setPosX(0); b.setPosY(0); b.setPosZ(0);
				} else {
					b.setRotX(-playerModel.leftArm.pitch);
					b.setRotY(-playerModel.leftArm.yaw);
					b.setRotZ(playerModel.leftArm.roll);
					b.setPosX(playerModel.leftArm.originX - 5.0F);
					b.setPosY(playerModel.leftArm.originY - 2.0F);
					b.setPosZ(playerModel.leftArm.originZ);
				}
			});
			bakedModel.getBone("anchor_rightarm").ifPresent(b -> {
				if (isIsolated) {
					b.setRotX(0); b.setRotY(0); b.setRotZ(0);
					b.setPosX(0); b.setPosY(0); b.setPosZ(0);
				} else {
					b.setRotX(-playerModel.rightArm.pitch);
					b.setRotY(-playerModel.rightArm.yaw);
					b.setRotZ(playerModel.rightArm.roll);
					b.setPosX(playerModel.rightArm.originX + 5.0F);
					b.setPosY(playerModel.rightArm.originY - 2.0F);
					b.setPosZ(playerModel.rightArm.originZ);
				}
			});
			bakedModel.getBone("anchor_leftleg").ifPresent(b -> {
				if (isIsolated) {
					b.setRotX(0); b.setRotY(0); b.setRotZ(0);
					b.setPosX(0); b.setPosY(0); b.setPosZ(0);
				} else {
					b.setRotX(-playerModel.leftLeg.pitch);
					b.setRotY(-playerModel.leftLeg.yaw);
					b.setRotZ(playerModel.leftLeg.roll);
					b.setPosX(playerModel.leftLeg.originX - 1.9F);
					b.setPosY(playerModel.leftLeg.originY - 12.0F);
					b.setPosZ(playerModel.leftLeg.originZ);
				}
			});
			bakedModel.getBone("anchor_rightleg").ifPresent(b -> {
				if (isIsolated) {
					b.setRotX(0); b.setRotY(0); b.setRotZ(0);
					b.setPosX(0); b.setPosY(0); b.setPosZ(0);
				} else {
					b.setRotX(-playerModel.rightLeg.pitch);
					b.setRotY(-playerModel.rightLeg.yaw);
					b.setRotZ(playerModel.rightLeg.roll);
					b.setPosX(playerModel.rightLeg.originX + 1.9F);
					b.setPosY(playerModel.rightLeg.originY - 12.0F);
					b.setPosZ(playerModel.rightLeg.originZ);
				}
			});
		});
		submit(matrices, this.animatable, playerState, queue, camera, effectiveLight, 0, positioner);
		*///?}
	}
}
//?}
