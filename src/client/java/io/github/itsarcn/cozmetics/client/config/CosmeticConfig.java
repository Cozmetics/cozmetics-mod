package io.github.itsarcn.cozmetics.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.itsarcn.cozmetics.Cozmetics;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Persistent configuration data for client cosmetics preferences.
 * Stored in JSON format in the Minecraft config directory.
 */
public class CosmeticConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * Map of category name to equipped cosmetic ID string (e.g. "PET" -> "cozmetics:angel_pet").
	 */
	public Map<String, String> equipped = new HashMap<>();

	/**
	 * Legacy map of category name to category visibility boolean.
	 */
	public Map<String, Boolean> visibility = new HashMap<>();

	/**
	 * Set of hidden cosmetic ID strings (e.g. "cozmetics:black_crown").
	 * Hiding affects rendering only and does not unequip the cosmetic.
	 */
	public Set<String> hiddenCosmetics = new HashSet<>();

	public static CosmeticConfig createDefault() {
		CosmeticConfig config = new CosmeticConfig();
		// All enabled categories are set to visible by default, but equipped starts completely empty
		for (CosmeticCategory category : CosmeticCategory.values()) {
			if (category.isEnabled()) {
				config.visibility.put(category.name(), true);
			}
		}
		return config;
	}

	public static CosmeticConfig load(Path configFile) {
		if (Files.exists(configFile)) {
			try (Reader reader = Files.newBufferedReader(configFile)) {
				CosmeticConfig config = GSON.fromJson(reader, CosmeticConfig.class);
				if (config != null) {
					if (config.equipped == null) config.equipped = new HashMap<>();
					if (config.visibility == null) config.visibility = new HashMap<>();
					if (config.hiddenCosmetics == null) config.hiddenCosmetics = new HashSet<>();
					return config;
				}
			} catch (Exception e) {
				Cozmetics.LOGGER.error("Failed to load cosmetic preferences from {}, using defaults", configFile, e);
			}
		}

		CosmeticConfig defaultConfig = createDefault();
		defaultConfig.save(configFile);
		return defaultConfig;
	}

	public void save(Path configFile) {
		try {
			if (configFile.getParent() != null) {
				Files.createDirectories(configFile.getParent());
			}
			try (Writer writer = Files.newBufferedWriter(configFile)) {
				GSON.toJson(this, writer);
			}
		} catch (Exception e) {
			Cozmetics.LOGGER.error("Failed to save cosmetic preferences to {}", configFile, e);
		}
	}
}

