package io.github.itsarcn.cozmetics.cosmetic;

import io.github.itsarcn.cozmetics.api.LocalDevelopmentCatalogueProvider;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Registry holding all available cosmetics in the mod.
 * Supports dynamic addition and lookup by identifier or category.
 * Decoupled from hardcoded content; populated via catalogue providers.
 */
public final class CosmeticRegistry {
	private static final Map<Identifier, Cosmetic> COSMETICS = new LinkedHashMap<>();

	/**
	 * Registers a cosmetic into the registry.
	 *
	 * @throws IllegalArgumentException if the cosmetic belongs to a disabled category
	 */
	public static <T extends Cosmetic> T register(T cosmetic) {
		if (!cosmetic.getCategory().isEnabled()) {
			throw new IllegalArgumentException("Cannot register cosmetic for disabled category: " + cosmetic.getCategory());
		}
		COSMETICS.put(cosmetic.getId(), cosmetic);
		return cosmetic;
	}

	/**
	 * Retrieves a cosmetic by its unique identifier.
	 */
	public static Optional<Cosmetic> get(Identifier id) {
		return Optional.ofNullable(COSMETICS.get(id));
	}

	/**
	 * Retrieves all registered cosmetics within a given category.
	 */
	public static List<Cosmetic> getByCategory(CosmeticCategory category) {
		List<Cosmetic> result = new ArrayList<>();
		for (Cosmetic cosmetic : COSMETICS.values()) {
			if (cosmetic.getCategory() == category) {
				result.add(cosmetic);
			}
		}
		return Collections.unmodifiableList(result);
	}

	/**
	 * Retrieves all registered cosmetics across all categories.
	 */
	public static Collection<Cosmetic> getAll() {
		return Collections.unmodifiableCollection(COSMETICS.values());
	}

	/**
	 * Clears all registered cosmetics. Useful for tests and reloading catalogues.
	 */
	public static void clear() {
		COSMETICS.clear();
	}

	/**
	 * Initializes the registry. Starts empty by default.
	 */
	public static void init() {
		// Starts clean and empty in production.
		// Dynamic catalogue providers (e.g. backend/API) register items as needed.
	}

	private CosmeticRegistry() {}
}
