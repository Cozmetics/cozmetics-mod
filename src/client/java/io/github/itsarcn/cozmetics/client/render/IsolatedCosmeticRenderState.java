package io.github.itsarcn.cozmetics.client.render;

import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Arm;

/**
 * Dedicated render state for rendering a cosmetic in isolation (without a player body).
 *
 * Configures invisibility flags so that Minecraft's living entity renderer skips the
 * player skin and body completely, rendering only the targeted cosmetic at viewport center.
 */
public class IsolatedCosmeticRenderState extends PlayerEntityRenderState {
	private final Cosmetic cosmetic;
	private final boolean thumbnail;

	public IsolatedCosmeticRenderState(Cosmetic cosmetic) {
		this(cosmetic, false);
	}

	public IsolatedCosmeticRenderState(Cosmetic cosmetic, boolean thumbnail) {
		super();
		this.cosmetic = cosmetic;
		this.thumbnail = thumbnail;

		// Set player model completely invisible
		this.invisible = true;
		this.invisibleToPlayer = true;
		this.hatVisible = false;
		this.jacketVisible = false;
		this.leftPantsLegVisible = false;
		this.rightPantsLegVisible = false;
		this.leftSleeveVisible = false;
		this.rightSleeveVisible = false;

		this.skinTextures = DefaultSkinHelper.getSteve();
		this.width = 0.6f;
		this.height = 1.8f;
		this.baseScale = 1.0f;
		this.standingEyeHeight = 1.62f;
		this.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
		this.rightArmPose = BipedEntityModel.ArmPose.EMPTY;
		this.preferredArm = Arm.RIGHT;

		// Real-time animation progress (frozen at frame 0 for static thumbnails)
		this.age = thumbnail ? 0.0f : (System.currentTimeMillis() % 100000000L) / 50.0f;
		this.bodyYaw = 180.0f;
		this.relativeHeadYaw = 0.0f;
		this.pitch = 0.0f;
		this.light = 0x00F000F0;
		this.shadowPieces.clear();
		this.outlineColor = 0;
	}

	public Cosmetic getCosmetic() {
		return cosmetic;
	}

	public boolean isThumbnail() {
		return thumbnail;
	}
}

