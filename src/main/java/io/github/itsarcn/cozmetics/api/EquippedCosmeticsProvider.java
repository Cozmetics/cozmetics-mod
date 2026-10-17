package io.github.itsarcn.cozmetics.api;

import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service boundary interface for retrieving equipped cosmetics and visibility state.
 *
 * Keeps player cosmetic equipment decoupled from network, account, or database layers.
 */
public interface EquippedCosmeticsProvider {
	/**
	 * Returns all cosmetics equipped by the specified player mapped by category.
	 */
	Map<CosmeticCategory, Cosmetic> getEquippedCosmetics(UUID playerUuid);

	/**
	 * Returns the cosmetic equipped in the specified category for a player, if any.
	 */
	Optional<Cosmetic> getEquipped(UUID playerUuid, CosmeticCategory category);

	/**
	 * Returns whether the specified cosmetic should be rendered for the player.
	 */
	boolean isCosmeticVisible(UUID playerUuid, Identifier cosmeticId);
}

