package io.github.itsarcn.cozmetics.cosmetic;

/**
 * Category enum defining all supported and planned cosmetic slots.
 * <p>
 * Supported categories: pets, wings, auras, headwear, fists, boots, face, and back accessories.
 * Note: Capes are reserved for a possible future PiCapes partnership and must remain disabled and unimplemented.
 */
public enum CosmeticCategory {
	PET("pet", "Pets", true),
	WINGS("wings", "Wings", true),
	AURA("aura", "Auras", true),
	HEADWEAR("headwear", "Headwear", true),
	FACE("face", "Face", true),
	BACK("back", "Back", true),
	FISTS("fists", "Fists", true),
	BOOTS("boots", "Boots", true),
	/**
	 * Capes are strictly reserved for a potential future PiCapes integration.
	 * Kept disabled and unimplemented.
	 */
	CAPE("cape", "Capes", false);

	private final String id;
	private final String displayName;
	private final boolean enabled;

	CosmeticCategory(String id, String displayName, boolean enabled) {
		this.id = id;
		this.displayName = displayName;
		this.enabled = enabled;
	}

	public String getId() {
		return this.id;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public boolean isEnabled() {
		return this.enabled;
	}
}
