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
 * Catalogue provider implementation backed by {@link CosmeticRegistry}.
 *
 * In production, the catalogue starts empty and is populated dynamically by external
 * sources (e.g. future Cozmetics website/API synchronization).
 * Optional development fixtures can be explicitly loaded via {@link #loadFixtures(InputStream)}.
 */
public class LocalDevelopmentCatalogueProvider implements CosmeticCatalogueProvider {
	private static final LocalDevelopmentCatalogueProvider INSTANCE = new LocalDevelopmentCatalogueProvider();

	public static LocalDevelopmentCatalogueProvider getInstance() {
		return INSTANCE;
	}

	/**
	 * Explicitly loads cosmetic fixtures from an input stream into the CosmeticRegistry.
	 * This is an opt-in method for testing and development environments only.
	 * Normal production and client launch NEVER automatically invokes this.
	 *
	 * @param in InputStream containing JSON fixtures
	 * @return Number of successfully loaded cosmetics
	 */
	public synchronized int loadFixtures(InputStream in) {
		if (in == null) {
			return 0;
		}

		int count = 0;
		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (root.has("cosmetics") && root.get("cosmetics").isJsonArray()) {
				JsonArray array = root.getAsJsonArray("cosmetics");
				for (JsonElement elem : array) {
					if (elem.isJsonObject()) {
						try {
							CosmeticDefinition def = CosmeticDefinition.fromJson(elem.getAsJsonObject());
							CosmeticRegistry.register(def);
							count++;
						} catch (Exception e) {
							Cozmetics.LOGGER.error("Failed to parse cosmetic fixture: {}", elem, e);
						}
					}
				}
				Cozmetics.LOGGER.info("Explicitly loaded {} cosmetic fixtures.", count);
			}
		} catch (Exception e) {
			Cozmetics.LOGGER.error("Failed to load cosmetic fixtures from stream", e);
		}
		return count;
	}

	@Override
	public Collection<Cosmetic> getAllCosmetics() {
		return CosmeticRegistry.getAll();
	}

	@Override
	public Collection<Cosmetic> getCosmeticsByCategory(CosmeticCategory category) {
		return CosmeticRegistry.getByCategory(category);
	}

	@Override
	public Optional<Cosmetic> getCosmetic(Identifier id) {
		return CosmeticRegistry.get(id);
	}
}
