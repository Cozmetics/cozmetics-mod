package io.github.itsarcn.cozmetics.api;

import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticRegistry;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Optional;

/**
 * Default offline implementation of CosmeticCatalogueProvider using local development fixtures.
 * Requires zero internet connection and ensures all registered local cosmetics are readily available.
 */
public class LocalDevelopmentCatalogueProvider implements CosmeticCatalogueProvider {
	private static final LocalDevelopmentCatalogueProvider INSTANCE = new LocalDevelopmentCatalogueProvider();

	public static LocalDevelopmentCatalogueProvider getInstance() {
		return INSTANCE;
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

