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
		if (state.invisible || state.spectator) {
			return;
		}

		ClientCosmeticManager manager = ClientCosmeticManager.getInstance();
		PlayerEntityModel playerModel = this.getContextModel();
		CosmeticRenderManager renderManager = CosmeticRenderManager.getInstance();

		for (CosmeticCategory category : CosmeticCategory.values()) {
			if (!category.isEnabled()) {
				continue;
			}

			if (!manager.isCategoryVisible(category)) {
				continue;
			}

			Cosmetic equipped = manager.getEquipped(category);
			if (equipped == null) {
				continue;
			}

			renderManager.render(equipped, matrices, queue, null, light, state, playerModel);
		}
	}
}

