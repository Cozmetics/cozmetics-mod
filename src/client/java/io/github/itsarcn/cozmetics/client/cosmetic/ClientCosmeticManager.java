package io.github.itsarcn.cozmetics.client.cosmetic;

import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticRegistry;

import java.util.EnumMap;
import java.util.Map;

/**
 * Client-side manager keeping track of equipped cosmetics for the local player.
 * For this first vertical slice, equips the Angel Pet by default.
 */
public class ClientCosmeticManager {
	private static final ClientCosmeticManager INSTANCE = new ClientCosmeticManager();
	private final Map<CosmeticCategory, Cosmetic> equippedCosmetics = new EnumMap<>(CosmeticCategory.class);

	private ClientCosmeticManager() {
		// Equip the Angel Pet by default so it's immediately active in local testing
		equip(CosmeticRegistry.ANGEL_PET);
	}

	public static ClientCosmeticManager getInstance() {
		return INSTANCE;
	}

	public void equip(Cosmetic cosmetic) {
		if (cosmetic != null && cosmetic.getCategory().isEnabled()) {
			this.equippedCosmetics.put(cosmetic.getCategory(), cosmetic);
		}
	}

	public void unequip(CosmeticCategory category) {
		this.equippedCosmetics.remove(category);
	}

	public Cosmetic getEquipped(CosmeticCategory category) {
		return this.equippedCosmetics.get(category);
	}

	public boolean hasEquipped(CosmeticCategory category) {
		return this.equippedCosmetics.containsKey(category);
	}
}

