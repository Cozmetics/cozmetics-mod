package io.github.itsarcn.cozmetics.client.cosmetic;

import io.github.itsarcn.cozmetics.Cozmetics;
import io.github.itsarcn.cozmetics.client.config.CosmeticConfig;
import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.util.*;

/**
 * Client-side manager keeping track of equipped cosmetics and item visibility
 * for the local player. Automatically saves preferences to disk and restores them
 * on game launch.
 *
 * Equipped state represents what cosmetics the user owns/has selected (eventually
 * synchronized from the website/backend).
 * Visibility state represents local client toggles to show/hide equipped cosmetics
 * during rendering without altering equipped data.
 */
public class ClientCosmeticManager {
	private static final ClientCosmeticManager INSTANCE = new ClientCosmeticManager();
	private final Path configFile;
	private final Map<CosmeticCategory, Cosmetic> equippedCosmetics = new EnumMap<>(CosmeticCategory.class);
	private final Set<Identifier> hiddenCosmetics = new HashSet<>();
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
		this.hiddenCosmetics.clear();

		CosmeticConfig config = CosmeticConfig.load(this.configFile);

		// Restore hidden cosmetics
		if (config.hiddenCosmetics != null) {
			for (String idStr : config.hiddenCosmetics) {
				try {
					this.hiddenCosmetics.add(Identifier.of(idStr));
				} catch (Exception ignored) {
				}
			}
		}

		// Restore category visibility (legacy)
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
		for (Identifier id : this.hiddenCosmetics) {
			config.hiddenCosmetics.add(id.toString());
		}
		config.save(this.configFile);
	}

	public synchronized boolean isCosmeticVisible(Identifier id) {
		return !this.hiddenCosmetics.contains(id);
	}

	public synchronized boolean isCosmeticVisible(Cosmetic cosmetic) {
		return cosmetic != null && isCosmeticVisible(cosmetic.getId());
	}

	public synchronized void setCosmeticVisible(Identifier id, boolean visible) {
		if (visible) {
			this.hiddenCosmetics.remove(id);
		} else {
			this.hiddenCosmetics.add(id);
		}
		save();
	}

	public synchronized void toggleCosmeticVisible(Identifier id) {
		setCosmeticVisible(id, !isCosmeticVisible(id));
	}

	public synchronized void showAll() {
		this.hiddenCosmetics.clear();
		save();
	}

	public synchronized void hideAll() {
		for (Cosmetic c : getAllEquipped()) {
			this.hiddenCosmetics.add(c.getId());
		}
		save();
	}

	public synchronized void solo(Identifier id) {
		boolean onlyThisVisible = isCosmeticVisible(id)
				&& getAllEquipped().stream().filter(this::isCosmeticVisible).count() == 1;
		if (onlyThisVisible) {
			showAll();
			return;
		}

		this.hiddenCosmetics.clear();
		for (Cosmetic c : getAllEquipped()) {
			if (!c.getId().equals(id)) {
				this.hiddenCosmetics.add(c.getId());
			}
		}
		save();
	}

	public synchronized List<Cosmetic> getAllEquipped() {
		List<Cosmetic> list = new ArrayList<>();
		for (CosmeticCategory cat : CosmeticCategory.values()) {
			Cosmetic c = this.equippedCosmetics.get(cat);
			if (c != null && cat.isEnabled()) {
				list.add(c);
			}
		}
		return list;
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
