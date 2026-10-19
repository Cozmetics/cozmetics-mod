package io.github.itsarcn.cozmetics.client.cosmetic;

import io.github.itsarcn.cozmetics.Cozmetics;
import io.github.itsarcn.cozmetics.catalog.CatalogItem;
import io.github.itsarcn.cozmetics.client.catalog.CosmeticCatalogManager;
import io.github.itsarcn.cozmetics.client.download.CosmeticAssetDownloader;
import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side multiplayer cosmetic tracker.
 *
 * Maps player UUIDs to their equipped cosmetic IDs. Supports seamless on-demand
 * background downloading and caching of remote players' cosmetics without altering
 * gameplay state or blocking the render thread.
 *
 * Provides client synchronization hooks ready for future backend/server packets.
 */
public class PlayerCosmeticTracker {

	private static final PlayerCosmeticTracker INSTANCE = new PlayerCosmeticTracker();

	// Remote player equipment map: UUID -> Map<Category, Cosmetic ID>
	private final Map<UUID, Map<CosmeticCategory, Identifier>> playerEquipped = new ConcurrentHashMap<>();

	// Track in-flight on-demand download requests per cosmetic ID to avoid duplicate downloads
	private final Set<Identifier> inFlightDownloads = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private PlayerCosmeticTracker() {}

	public static PlayerCosmeticTracker getInstance() {
		return INSTANCE;
	}

	/**
	 * Gets the equipped Cosmetic for a player by category.
	 *
	 * For the local player, delegates to ClientCosmeticManager.
	 * For remote players, checks the registry; if not yet loaded in memory,
	 * automatically triggers an on-demand background asset package download.
	 */
	public @Nullable Cosmetic getEquipped(UUID playerUuid, CosmeticCategory category) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null && client.player.getUuid().equals(playerUuid)) {
			Cosmetic local = ClientCosmeticManager.getInstance().getEquipped(category);
			if (local != null && ClientCosmeticManager.getInstance().isCosmeticVisible(local.getId())) {
				return local;
			}
			return null;
		}

		Map<CosmeticCategory, Identifier> map = playerEquipped.get(playerUuid);
		if (map == null) {
			return null;
		}

		Identifier cosmeticId = map.get(category);
		if (cosmeticId == null) {
			return null;
		}

		// 1. Check if already loaded and registered in runtime registry
		Optional<Cosmetic> cosmeticOpt = CosmeticRegistry.get(cosmeticId);
		if (cosmeticOpt.isPresent()) {
			return cosmeticOpt.get();
		}

		// 2. Trigger on-demand download in background if not already downloading
		requestOnDemandDownload(cosmeticId);
		return null;
	}

	private void requestOnDemandDownload(Identifier cosmeticId) {
		if (!inFlightDownloads.add(cosmeticId)) {
			return;
		}

		Optional<CatalogItem> itemOpt = CosmeticCatalogManager.getInstance().getItem(cosmeticId);
		if (itemOpt.isPresent()) {
			CatalogItem item = itemOpt.get();
			Cozmetics.LOGGER.info("Remote player wearing unloaded cosmetic '{}'; initiating on-demand download", cosmeticId);
			CosmeticAssetDownloader.getInstance().downloadAndLoadPackageAsync(item).whenComplete((res, ex) -> {
				inFlightDownloads.remove(cosmeticId);
				if (ex != null) {
					Cozmetics.LOGGER.warn("Failed to download remote cosmetic package '{}': {}", cosmeticId, ex.getMessage());
				}
			});
		} else {
			inFlightDownloads.remove(cosmeticId);
		}
	}

	/**
	 * Updates a player's equipped cosmetic for a category.
	 * Hook point for future S2C packet reception or HTTP poll updates.
	 */
	public void setPlayerEquipped(UUID playerUuid, CosmeticCategory category, @Nullable Identifier cosmeticId) {
		if (cosmeticId == null) {
			Map<CosmeticCategory, Identifier> map = playerEquipped.get(playerUuid);
			if (map != null) {
				map.remove(category);
			}
		} else {
			playerEquipped.computeIfAbsent(playerUuid, k -> new EnumMap<>(CosmeticCategory.class))
					.put(category, cosmeticId);
		}
	}

	/**
	 * Updates all equipped cosmetics for a player in a single call.
	 */
	public void setPlayerEquippedBatch(UUID playerUuid, Map<CosmeticCategory, Identifier> equipped) {
		if (equipped == null || equipped.isEmpty()) {
			playerEquipped.remove(playerUuid);
		} else {
			Map<CosmeticCategory, Identifier> map = new EnumMap<>(CosmeticCategory.class);
			for (Map.Entry<CosmeticCategory, Identifier> e : equipped.entrySet()) {
				if (e.getKey().isEnabled() && e.getValue() != null) {
					map.put(e.getKey(), e.getValue());
				}
			}
			playerEquipped.put(playerUuid, map);
		}
	}

	/**
	 * Clears tracking for a disconnected player.
	 */
	public void clearPlayer(UUID playerUuid) {
		playerEquipped.remove(playerUuid);
	}

	/**
	 * Clears all remote player tracking state.
	 */
	public void clearAll() {
		playerEquipped.clear();
		inFlightDownloads.clear();
	}
}

