package io.github.itsarcn.cozmetics.cosmetic;

import io.github.itsarcn.cozmetics.Cozmetics;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Registry holding all available cosmetics in the mod.
 * Supports dynamic addition and lookup by identifier or category.
 */
public final class CosmeticRegistry {
	private static final Map<Identifier, Cosmetic> COSMETICS = new LinkedHashMap<>();

	// 1. Pets
	public static final Cosmetic ANGEL_PET = register(new AngelPetCosmetic());

	// 2. Wings
	public static final Cosmetic BAT_WINGS = register(CosmeticDefinition.builder(
			Cozmetics.id("bat_wings"),
			CosmeticCategory.WINGS,
			"Halloween Bat Wings V2"
	)
			.model(Cozmetics.id("cosmetics/wings/bat_wings"))
			.texture(Cozmetics.id("textures/cosmetics/wings/bat_wings.png"))
			.animation(Cozmetics.id("cosmetics/wings/bat_wings"), "animation.halloween2024BatWingsV2.idle")
			.slot(AttachmentSlot.BODY)
			.build());

	// 3. Auras
	public static final Cosmetic CORRUPTED_AURA = register(CosmeticDefinition.builder(
			Cozmetics.id("corrupted_aura"),
			CosmeticCategory.AURA,
			"Corrupted Aura"
	)
			.model(Cozmetics.id("cosmetics/auras/corrupted_aura"))
			.texture(Cozmetics.id("textures/cosmetics/auras/corrupted_aura.png"))
			.animation(Cozmetics.id("cosmetics/auras/corrupted_aura"), "animation.beenTakenCorruptedAura.idle")
			.slot(AttachmentSlot.BODY)
			.build());

	// 4. Back
	public static final Cosmetic MEDAL_SCYTHE = register(CosmeticDefinition.builder(
			Cozmetics.id("medal_scythe"),
			CosmeticCategory.BACK,
			"Medal Scythe"
	)
			.model(Cozmetics.id("cosmetics/back/medal_scythe"))
			.texture(Cozmetics.id("textures/cosmetics/back/medal_scythe.png"))
			.animation(Cozmetics.id("cosmetics/back/medal_scythe"), "animation.medalScythe.idle")
			.slot(AttachmentSlot.BODY)
			.build());

	// 5. Boots
	public static final Cosmetic GAMER_RGB_BOOTS = register(CosmeticDefinition.builder(
			Cozmetics.id("gamer_rgb_boots"),
			CosmeticCategory.BOOTS,
			"Gamer RGB Boots"
	)
			.model(Cozmetics.id("cosmetics/boots/gamer_rgb_boots"))
			.texture(Cozmetics.id("textures/cosmetics/boots/gamer_rgb_boots.png"))
			.slot(AttachmentSlot.LEGS)
			.build());

	// 6. Faces
	public static final Cosmetic CROW_MASK = register(CosmeticDefinition.builder(
			Cozmetics.id("crow_mask"),
			CosmeticCategory.FACE,
			"Demon Crow Mask"
	)
			.model(Cozmetics.id("cosmetics/faces/crow_mask"))
			.texture(Cozmetics.id("textures/cosmetics/faces/crow_mask.png"))
			.slot(AttachmentSlot.HEAD)
			.build());

	// 7. Fists
	public static final Cosmetic CORRUPTED_FISTS = register(CosmeticDefinition.builder(
			Cozmetics.id("corrupted_fists"),
			CosmeticCategory.FISTS,
			"Corrupted Fists"
	)
			.model(Cozmetics.id("cosmetics/fists/corrupted_fists"))
			.texture(Cozmetics.id("textures/cosmetics/fists/corrupted_fists.png"))
			.slot(AttachmentSlot.ARMS)
			.build());

	// 8. Headwear
	public static final Cosmetic BLACK_CROWN = register(CosmeticDefinition.builder(
			Cozmetics.id("black_crown"),
			CosmeticCategory.HEADWEAR,
			"Black Crown"
	)
			.model(Cozmetics.id("cosmetics/headwear/black_crown"))
			.texture(Cozmetics.id("textures/cosmetics/headwear/black_crown.png"))
			.slot(AttachmentSlot.HEAD)
			.transform(new AttachmentTransform(3.3f, 1.0f, true))
			.build());

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

	public static List<Cosmetic> getByCategory(CosmeticCategory category) {
		List<Cosmetic> result = new ArrayList<>();
		for (Cosmetic cosmetic : COSMETICS.values()) {
			if (cosmetic.getCategory() == category) {
				result.add(cosmetic);
			}
		}
		return Collections.unmodifiableList(result);
	}

	public static Collection<Cosmetic> getAll() {
		return Collections.unmodifiableCollection(COSMETICS.values());
	}

	public static void init() {
		// Ensures static initializer runs
	}

	private CosmeticRegistry() {}
}
