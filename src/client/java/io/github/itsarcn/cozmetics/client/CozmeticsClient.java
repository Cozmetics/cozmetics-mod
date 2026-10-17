package io.github.itsarcn.cozmetics.client;

import io.github.itsarcn.cozmetics.Cozmetics;
import io.github.itsarcn.cozmetics.client.render.PetFeatureRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.EntityType;

public class CozmeticsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Cozmetics.LOGGER.info("Initializing Cozmetics client...");

		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityType == EntityType.PLAYER && entityRenderer instanceof PlayerEntityRenderer playerRenderer) {
				registrationHelper.register(new PetFeatureRenderer(playerRenderer));
			}
		});
	}
}