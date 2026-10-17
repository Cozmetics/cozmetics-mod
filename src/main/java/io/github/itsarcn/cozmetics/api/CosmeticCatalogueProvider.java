package io.github.itsarcn.cozmetics.api;

import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Optional;

/**
 * Service boundary interface for cosmetic catalogue discovery.
 *
 * Decouples cosmetic registration and rendering from how cosmetics are sourced,
 * supporting both local development fixtures and future backend/API providers
 * without modifying rendering code.
 */
public interface CosmeticCatalogueProvider {
	/**
	 * Returns all cosmetics currently provided by this catalogue.
	 */
	Collection<Cosmetic> getAllCosmetics();

	/**
	 * Returns all cosmetics in the given category.
	 */
	Collection<Cosmetic> getCosmeticsByCategory(CosmeticCategory category);

	/**
	 * Looks up a cosmetic by its unique identifier.
	 */
	Optional<Cosmetic> getCosmetic(Identifier id);

	/**
	 * Checks if a cosmetic with the specified identifier is available.
	 */
	default boolean hasCosmetic(Identifier id) {
		return getCosmetic(id).isPresent();
	}
}

