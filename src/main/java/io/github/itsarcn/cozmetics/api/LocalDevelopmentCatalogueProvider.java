package io.github.itsarcn.cozmetics.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.itsarcn.cozmetics.Cozmetics;
import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticDefinition;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticRegistry;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;

/**
 * Default offline implementation of CosmeticCatalogueProvider using local development fixtures.
 * Requires zero internet connection and loads registered cosmetics from development_fixtures.json.
 */
public class LocalDevelopmentCatalogueProvider implements CosmeticCatalogueProvider {
	private static final LocalDevelopmentCatalogueProvider INSTANCE = new LocalDevelopmentCatalogueProvider();
	private static final String FIXTURES_PATH = "/assets/cozmetics/catalogue/development_fixtures.json";
	private boolean loaded = false;

	public static LocalDevelopmentCatalogueProvider getInstance() {
		return INSTANCE;
	}

	/**
	 * Loads development fixtures into the CosmeticRegistry.
	 * Can be safely called multiple times; will only load once unless reloaded.
	 */
	public synchronized void load() {
		if (this.loaded) {
			return;
		}

		try (InputStream in = getClass().getResourceAsStream(FIXTURES_PATH)) {
			if (in == null) {
				Cozmetics.LOGGER.warn("Local development fixtures not found at {}", FIXTURES_PATH);
				return;
			}
			try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				if (root.has("cosmetics") && root.get("cosmetics").isJsonArray()) {
					JsonArray array = root.getAsJsonArray("cosmetics");
					for (JsonElement elem : array) {
						if (elem.isJsonObject()) {
							try {
								CosmeticDefinition def = CosmeticDefinition.fromJson(elem.getAsJsonObject());
								CosmeticRegistry.register(def);
							} catch (Exception e) {
								Cozmetics.LOGGER.error("Failed to parse cosmetic fixture: {}", elem, e);
							}
						}
					}
					this.loaded = true;
					Cozmetics.LOGGER.info("Loaded {} cosmetic fixtures from {}", CosmeticRegistry.getAll().size(), FIXTURES_PATH);
				}
			}
		} catch (Exception e) {
			Cozmetics.LOGGER.error("Failed to load local development catalogue fixtures from {}", FIXTURES_PATH, e);
		}
	}

	public synchronized void reload() {
		this.loaded = false;
		load();
	}

	@Override
	public Collection<Cosmetic> getAllCosmetics() {
		if (!this.loaded) {
			load();
		}
		return CosmeticRegistry.getAll();
	}

	@Override
	public Collection<Cosmetic> getCosmeticsByCategory(CosmeticCategory category) {
		if (!this.loaded) {
			load();
		}
		return CosmeticRegistry.getByCategory(category);
	}

	@Override
	public Optional<Cosmetic> getCosmetic(Identifier id) {
		if (!this.loaded) {
			load();
		}
		return CosmeticRegistry.get(id);
	}
}
