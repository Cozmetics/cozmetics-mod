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
import net.minecraft.client.gui.widget.ClickableWidget;
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

		// Add "Cozmetics" button dynamically in the pause menu (GameMenuScreen) and title screen (TitleScreen)
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof TitleScreen) {
				int buttonWidth = 200;
				int buttonHeight = 20;
				int buttonX = screen.width / 2 - 100;

				var buttons = Screens.getButtons(screen);
				ClickableWidget modsBtn = null;
				ClickableWidget realmsBtn = null;
				ClickableWidget mpBtn = null;
				ClickableWidget optionsBtn = null;

				for (ClickableWidget btn : buttons) {
					String text = btn.getMessage().getString().toLowerCase();
					if (text.contains("mods")) {
						modsBtn = btn;
					} else if (text.contains("realms")) {
						realmsBtn = btn;
					} else if (text.contains("multiplayer")) {
						mpBtn = btn;
					}
					if (text.contains("options")) {
						optionsBtn = btn;
					}
				}

				ClickableWidget anchorBtn = modsBtn != null ? modsBtn : (realmsBtn != null ? realmsBtn : mpBtn);

				int buttonY;
				if (anchorBtn != null) {
					buttonY = anchorBtn.getY() + 24;
				} else if (optionsBtn != null) {
					buttonY = optionsBtn.getY();
				} else {
					buttonY = screen.height / 4 + 48 + 72;
				}

				// Shift down any existing buttons that would overlap or collide below target Y
				for (ClickableWidget btn : buttons) {
					if (btn.getY() >= buttonY) {
						btn.setY(btn.getY() + 24);
					}
				}

				ButtonWidget cozmeticsButton = ButtonWidget.builder(
						Text.literal("Cozmetics"),
						btn -> client.setScreen(new CosmeticsScreen(screen))
				).dimensions(buttonX, buttonY, buttonWidth, buttonHeight).build();

				buttons.add(cozmeticsButton);
			} else if (screen instanceof GameMenuScreen) {
				int buttonWidth = 204;
				int buttonHeight = 20;
				int buttonX = screen.width / 2 - 102;

				var buttons = Screens.getButtons(screen);
				ClickableWidget modsBtn = null;
				ClickableWidget exitBtn = null;

				for (ClickableWidget btn : buttons) {
					String text = btn.getMessage().getString().toLowerCase();
					if (text.contains("mods")) {
						modsBtn = btn;
					} else if (text.contains("disconnect") || text.contains("title") || text.contains("quit")) {
						if (exitBtn == null || btn.getY() > exitBtn.getY()) {
							exitBtn = btn;
						}
					}
				}

				int buttonY;
				if (modsBtn != null) {
					buttonY = modsBtn.getY() + 24;
				} else if (exitBtn != null) {
					buttonY = exitBtn.getY();
				} else {
					buttonY = screen.height / 4 + 120;
				}

				for (ClickableWidget btn : buttons) {
					if (btn.getY() >= buttonY) {
						btn.setY(btn.getY() + 24);
					}
				}

				ButtonWidget cozmeticsButton = ButtonWidget.builder(
						Text.literal("Cozmetics"),
						btn -> client.setScreen(new CosmeticsScreen(screen))
				).dimensions(buttonX, buttonY, buttonWidth, buttonHeight).build();

				buttons.add(cozmeticsButton);
			}
		});
	}
}