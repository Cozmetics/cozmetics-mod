package io.github.itsarcn.cozmetics.api;

import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticRegistry;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Local offline development implementation of {@link EquippedCosmeticsProvider}.
 * Keeps player cosmetic equipment decoupled from network, account, or database layers.
 */
public class LocalEquippedCosmeticsProvider implements EquippedCosmeticsProvider {
	private static final LocalEquippedCosmeticsProvider INSTANCE = new LocalEquippedCosmeticsProvider();
	private final Map<UUID, Map<CosmeticCategory, Cosmetic>> playerEquipped = new HashMap<>();
	private final Map<UUID, Set<Identifier>> playerHidden = new HashMap<>();

	public static LocalEquippedCosmeticsProvider getInstance() {
		return INSTANCE;
	}

	@Override
	public Map<CosmeticCategory, Cosmetic> getEquippedCosmetics(UUID playerUuid) {
		return Collections.unmodifiableMap(playerEquipped.computeIfAbsent(playerUuid, u -> {
			Map<CosmeticCategory, Cosmetic> map = new EnumMap<>(CosmeticCategory.class);
			for (CosmeticCategory cat : CosmeticCategory.values()) {
				if (cat.isEnabled()) {
					List<Cosmetic> list = CosmeticRegistry.getByCategory(cat);
					if (!list.isEmpty()) {
						map.put(cat, list.getFirst());
					}
				}
			}
			return map;
		}));
	}

	@Override
	public Optional<Cosmetic> getEquipped(UUID playerUuid, CosmeticCategory category) {
		return Optional.ofNullable(getEquippedCosmetics(playerUuid).get(category));
	}

	@Override
	public boolean isCosmeticVisible(UUID playerUuid, Identifier cosmeticId) {
		Set<Identifier> hidden = playerHidden.get(playerUuid);
		return hidden == null || !hidden.contains(cosmeticId);
	}

	public void setCosmeticVisible(UUID playerUuid, Identifier cosmeticId, boolean visible) {
		Set<Identifier> hidden = playerHidden.computeIfAbsent(playerUuid, u -> new HashSet<>());
		if (visible) {
			hidden.remove(cosmeticId);
		} else {
			hidden.add(cosmeticId);
		}
	}
}

