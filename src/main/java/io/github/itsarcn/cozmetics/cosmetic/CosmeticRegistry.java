package io.github.itsarcn.cozmetics.cosmetic;

import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry holding all available cosmetics in the mod.
 */
public final class CosmeticRegistry {
	private static final Map<Identifier, Cosmetic> COSMETICS = new LinkedHashMap<>();

	public static final Cosmetic ANGEL_PET = register(new AngelPetCosmetic());

	public static <T extends Cosmetic> T register(T cosmetic) {
		if (!cosmetic.getCategory().isEnabled()) {
			throw new IllegalArgumentException("Cannot register cosmetic for disabled category: " + cosmetic.getCategory());
		}
		COSMETICS.put(cosmetic.getId(), cosmetic);
		return cosmetic;
	}

	public static Optional<Cosmetic> get(Identifier id) {
		return Optional.ofNullable(COSMETICS.get(id));
	}

	public static Map<Identifier, Cosmetic> getAll() {
		return Collections.unmodifiableMap(COSMETICS);
	}

	public static void init() {
		// Ensures static initializer runs
	}

	private CosmeticRegistry() {}
}

