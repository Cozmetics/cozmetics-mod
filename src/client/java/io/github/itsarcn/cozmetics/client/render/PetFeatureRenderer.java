package io.github.itsarcn.cozmetics.client.render;

import io.github.itsarcn.cozmetics.client.cosmetic.ClientCosmeticManager;
import io.github.itsarcn.cozmetics.client.cosmetic.pet.AngelPetRenderer;
import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;

/**
 * FeatureRenderer that renders equipped pet cosmetics on players.
 */
public class PetFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
	private final AngelPetRenderer petRenderer = new AngelPetRenderer();

	public PetFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
		if (state.invisible || state.spectator) {
			return;
		}

		Cosmetic equippedPet = ClientCosmeticManager.getInstance().getEquipped(CosmeticCategory.PET);
		if (equippedPet == null || !ClientCosmeticManager.getInstance().isCosmeticVisible(equippedPet.getId())) {
			return;
		}

		petRenderer.renderPet(matrices, queue, null, light, state);
	}
}
