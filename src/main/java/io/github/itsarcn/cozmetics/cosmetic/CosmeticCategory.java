package io.github.itsarcn.cozmetics.cosmetic;

/**
 * Category enum defining all supported and planned cosmetic slots.
 * <p>
 * Prepared for future categories (pets, wings, auras, headwear, fists, boots, back accessories).
 * Note: Capes are reserved for a possible future PiCapes partnership and must remain disabled and unimplemented.
 */
public enum CosmeticCategory {
	PET("pet", true),
	WINGS("wings", true),
	AURA("aura", true),
	HEADWEAR("headwear", true),
	FISTS("fists", true),
	BOOTS("boots", true),
	BACK("back", true),
	/**
	 * Capes are strictly reserved for a potential future PiCapes integration.
	 * Kept disabled and unimplemented.
	 */
	CAPE("cape", false);

	private final String id;
	private final boolean enabled;

	CosmeticCategory(String id, boolean enabled) {
		this.id = id;
		this.enabled = enabled;
	}

	public String getId() {
		return this.id;
	}

	public boolean isEnabled() {
		return this.enabled;
	}
}

