package io.github.itsarcn.cozmetics.client;

import io.github.itsarcn.cozmetics.Cozmetics;
import io.github.itsarcn.cozmetics.client.gui.CosmeticsScreen;
import io.github.itsarcn.cozmetics.client.render.CosmeticFeatureRenderer;
import io.github.itsarcn.cozmetics.network.HttpCosmeticsClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;

public class CozmeticsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Cozmetics.LOGGER.info("Initializing Cozmetics client...");

		// Register cosmetic feature renderer on all player renderers (default & slim)
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityType == EntityType.PLAYER && entityRenderer instanceof PlayerEntityRenderer playerRenderer) {
				registrationHelper.register(new CosmeticFeatureRenderer(playerRenderer));
			}
		});

		// Trigger non-blocking asynchronous catalogue sync on client startup
		HttpCosmeticsClient.getInstance().syncCatalogue();

		// Add "✦ Cozmetics" button in the pause menu (GameMenuScreen) and title screen (TitleScreen)
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof GameMenuScreen || screen instanceof TitleScreen) {
				int buttonWidth = 100;
				int buttonHeight = 20;
				int buttonX = screen.width - buttonWidth - 10;
				int buttonY = 10;

				ButtonWidget cozmeticsButton = ButtonWidget.builder(
						Text.literal("✦ Cozmetics"),
						btn -> client.setScreen(new CosmeticsScreen(screen))
				).dimensions(buttonX, buttonY, buttonWidth, buttonHeight).build();

				Screens.getButtons(screen).add(cozmeticsButton);
			}
		});
	}
}