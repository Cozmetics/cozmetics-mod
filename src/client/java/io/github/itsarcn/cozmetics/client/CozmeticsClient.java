package io.github.itsarcn.cozmetics.client;

import io.github.itsarcn.cozmetics.Cozmetics;
import io.github.itsarcn.cozmetics.client.gui.CosmeticsScreen;
import io.github.itsarcn.cozmetics.client.render.CosmeticFeatureRenderer;
import io.github.itsarcn.cozmetics.network.HttpCosmeticsClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.EntityType;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

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

		// Register client-side commands (/cozmetics link, /cozmetics sync)
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("cozmetics")
					.then(ClientCommandManager.literal("link").executes(context -> {
						var client = context.getSource().getClient();
						if (client.player == null) {
							context.getSource().sendError(Text.literal("Player not found."));
							return 0;
						}
						String username = client.player.getName().getString();
						UUID uuid = client.player.getUuid();
						context.getSource().sendFeedback(Text.literal("§6[Cozmetics] §7Requesting account linking code from API..."));

						HttpCosmeticsClient.getInstance().requestDeviceLink(username, uuid)
								.thenAccept(response -> {
									client.execute(() -> {
										String webUrl = "http://localhost:5173/link?code=" + response.code();
										context.getSource().sendFeedback(Text.literal("§6[Cozmetics] §aLinking code generated: §e§l" + response.code()));
										context.getSource().sendFeedback(
												Text.literal("§6[Cozmetics] §b✦ Click here to link your account on web")
														.styled(style -> style
																.withColor(Formatting.AQUA)
																.withUnderline(true)
																.withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(webUrl)))
																.withHoverEvent(new HoverEvent.ShowText(Text.literal("Open " + webUrl)))
														)
										);
										context.getSource().sendFeedback(Text.literal("§6[Cozmetics] §7Code expires in " + (response.expiresInSeconds() / 60) + " minutes."));
									});
								})
								.exceptionally(ex -> {
									client.execute(() -> {
										context.getSource().sendError(Text.literal("§c[Cozmetics] Failed to connect to server: " + ex.getMessage()));
									});
									return null;
								});
						return 1;
					}))
					.then(ClientCommandManager.literal("sync").executes(context -> {
						var client = context.getSource().getClient();
						context.getSource().sendFeedback(Text.literal("§6[Cozmetics] §7Synchronizing cosmetics with backend..."));
						HttpCosmeticsClient.getInstance().syncCatalogue()
								.thenAccept(count -> {
									client.execute(() -> {
										context.getSource().sendFeedback(Text.literal("§6[Cozmetics] §aSuccessfully synchronized " + count + " cosmetics!"));
									});
								})
								.exceptionally(ex -> {
									client.execute(() -> {
										context.getSource().sendError(Text.literal("§c[Cozmetics] Sync failed: " + ex.getMessage()));
									});
									return null;
								});
						return 1;
					}))
			);
		});

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