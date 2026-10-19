package io.github.itsarcn.cozmetics.client.render;

import io.github.itsarcn.cozmetics.client.cosmetic.ClientCosmeticManager;
import io.github.itsarcn.cozmetics.client.render.dynamic.CosmeticRenderManager;
import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import java.util.UUID;

/**
 * Unified feature renderer that renders all equipped, visible cosmetics for players.
 * Supports pets, wings, auras, headwear, face, back, fists, and boots.
 */
public class CosmeticFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
	public CosmeticFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
		CosmeticRenderManager renderManager = CosmeticRenderManager.getInstance();

		// 1. Isolated Preview Mode (Cosmetic-Only in Sidebar)
		if (state instanceof IsolatedCosmeticRenderState isolatedState) {
			Cosmetic cosmetic = isolatedState.getCosmetic();
			if (cosmetic != null) {
				renderManager.render(cosmetic, matrices, queue, null, light, state, this.getContextModel());
			}
			return;
		}

		if (state.invisible || state.spectator) {
			return;
		}

		ClientCosmeticManager manager = ClientCosmeticManager.getInstance();
		PlayerEntityModel playerModel = this.getContextModel();
		io.github.itsarcn.cozmetics.client.cosmetic.PlayerCosmeticTracker tracker =
				io.github.itsarcn.cozmetics.client.cosmetic.PlayerCosmeticTracker.getInstance();

		net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
		UUID playerUuid = null;
		if (mc != null && mc.player != null && state.id == mc.player.getId()) {
			playerUuid = mc.player.getUuid();
		} else if (mc != null && mc.world != null) {
			net.minecraft.entity.Entity entity = mc.world.getEntityById(state.id);
			if (entity != null) {
				playerUuid = entity.getUuid();
			}
		}

		for (CosmeticCategory category : CosmeticCategory.values()) {
			if (!category.isEnabled()) {
				continue;
			}

			Cosmetic equipped = (playerUuid != null) ? tracker.getEquipped(playerUuid, category) : manager.getEquipped(category);
			if (equipped == null) {
				continue;
			}

			// Local client item-level visibility toggle
			if (playerUuid != null && mc != null && mc.player != null && playerUuid.equals(mc.player.getUuid())) {
				if (!manager.isCosmeticVisible(equipped.getId())) {
					continue;
				}
			}

			renderManager.render(equipped, matrices, queue, null, light, state, playerModel);
		}
	}
}

