package io.github.itsarcn.cozmetics.client.cosmetic;

import io.github.itsarcn.cozmetics.Cozmetics;
import io.github.itsarcn.cozmetics.client.config.CosmeticConfig;
import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Client-side manager keeping track of equipped cosmetics and category visibility
 * for the local player. Automatically saves preferences to disk and restores them
 * on game launch.
 */
public class ClientCosmeticManager {
	private static final ClientCosmeticManager INSTANCE = new ClientCosmeticManager();
	private final Path configFile;
	private final Map<CosmeticCategory, Cosmetic> equippedCosmetics = new EnumMap<>(CosmeticCategory.class);
	private final Map<CosmeticCategory, Boolean> categoryVisibility = new EnumMap<>(CosmeticCategory.class);

	private ClientCosmeticManager() {
		this.configFile = FabricLoader.getInstance().getConfigDir().resolve("cozmetics.json");
		reload();
	}

	public static ClientCosmeticManager getInstance() {
		return INSTANCE;
	}

	/**
	 * Reloads preferences from disk, validating equipped cosmetics against the registry.
	 */
	public synchronized void reload() {
		this.equippedCosmetics.clear();
		this.categoryVisibility.clear();

		CosmeticConfig config = CosmeticConfig.load(this.configFile);

		// Restore category visibility
		for (CosmeticCategory category : CosmeticCategory.values()) {
			if (!category.isEnabled()) {
				continue;
			}
			boolean visible = config.visibility.getOrDefault(category.name(), true);
			this.categoryVisibility.put(category, visible);
		}

		// Restore equipped cosmetics
		for (Map.Entry<String, String> entry : config.equipped.entrySet()) {
			try {
				CosmeticCategory category = CosmeticCategory.valueOf(entry.getKey());
				if (!category.isEnabled()) {
					continue;
				}
				Identifier id = Identifier.of(entry.getValue());
				Optional<Cosmetic> cosmeticOpt = CosmeticRegistry.get(id);
				if (cosmeticOpt.isPresent()) {
					Cosmetic cosmetic = cosmeticOpt.get();
					if (cosmetic.getCategory() == category) {
						this.equippedCosmetics.put(category, cosmetic);
					}
				} else {
					Cozmetics.LOGGER.warn("Saved cosmetic ID '{}' not found in registry; skipping.", id);
				}
			} catch (Exception e) {
				Cozmetics.LOGGER.warn("Invalid config entry: {} -> {}", entry.getKey(), entry.getValue(), e);
			}
		}

		// If nothing was equipped for PET (e.g. first run), ensure default Angel Pet is equipped
		if (!this.equippedCosmetics.containsKey(CosmeticCategory.PET) && CosmeticCategory.PET.isEnabled()) {
			this.equippedCosmetics.put(CosmeticCategory.PET, CosmeticRegistry.ANGEL_PET);
			save();
		}
	}

	/**
	 * Persists current equipped cosmetics and visibility preferences to disk.
	 */
	public synchronized void save() {
		CosmeticConfig config = new CosmeticConfig();
		for (Map.Entry<CosmeticCategory, Cosmetic> entry : this.equippedCosmetics.entrySet()) {
			if (entry.getKey().isEnabled() && entry.getValue() != null) {
				config.equipped.put(entry.getKey().name(), entry.getValue().getId().toString());
			}
		}
		for (Map.Entry<CosmeticCategory, Boolean> entry : this.categoryVisibility.entrySet()) {
			if (entry.getKey().isEnabled()) {
				config.visibility.put(entry.getKey().name(), entry.getValue());
			}
		}
		config.save(this.configFile);
	}

	public synchronized void equip(Cosmetic cosmetic) {
		if (cosmetic != null && cosmetic.getCategory().isEnabled()) {
			this.equippedCosmetics.put(cosmetic.getCategory(), cosmetic);
			save();
		}
	}

	public synchronized void unequip(CosmeticCategory category) {
		if (this.equippedCosmetics.remove(category) != null) {
			save();
		}
	}

	public synchronized Cosmetic getEquipped(CosmeticCategory category) {
		return this.equippedCosmetics.get(category);
	}

	public synchronized boolean hasEquipped(CosmeticCategory category) {
		return this.equippedCosmetics.containsKey(category);
	}

	public synchronized boolean isCategoryVisible(CosmeticCategory category) {
		return this.categoryVisibility.getOrDefault(category, true);
	}

	public synchronized void setCategoryVisible(CosmeticCategory category, boolean visible) {
		if (category.isEnabled()) {
			this.categoryVisibility.put(category, visible);
			save();
		}
	}

	public synchronized void toggleCategoryVisible(CosmeticCategory category) {
		setCategoryVisible(category, !isCategoryVisible(category));
	}
}
