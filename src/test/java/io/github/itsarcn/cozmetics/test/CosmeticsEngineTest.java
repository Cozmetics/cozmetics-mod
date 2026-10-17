package io.github.itsarcn.cozmetics.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.itsarcn.cozmetics.Cozmetics;
import io.github.itsarcn.cozmetics.client.config.CosmeticConfig;
import io.github.itsarcn.cozmetics.cosmetic.*;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CosmeticsEngineTest {

	@BeforeAll
	static void setup() {
		CosmeticRegistry.init();
	}

	@Test
	void testAllSampleCosmeticsRegistered() {
		Collection<Cosmetic> all = CosmeticRegistry.getAll();
		assertEquals(8, all.size(), "Should have exactly 8 sample cosmetics registered");

		// Verify 8 categories
		assertNotNull(CosmeticRegistry.get(Cozmetics.id("angel_pet")).orElse(null));
		assertNotNull(CosmeticRegistry.get(Cozmetics.id("bat_wings")).orElse(null));
		assertNotNull(CosmeticRegistry.get(Cozmetics.id("corrupted_aura")).orElse(null));
		assertNotNull(CosmeticRegistry.get(Cozmetics.id("medal_scythe")).orElse(null));
		assertNotNull(CosmeticRegistry.get(Cozmetics.id("gamer_rgb_boots")).orElse(null));
		assertNotNull(CosmeticRegistry.get(Cozmetics.id("crow_mask")).orElse(null));
		assertNotNull(CosmeticRegistry.get(Cozmetics.id("corrupted_fists")).orElse(null));
		assertNotNull(CosmeticRegistry.get(Cozmetics.id("black_crown")).orElse(null));
	}

	@Test
	void testCategoriesAndAttachmentSlots() {
		Cosmetic angelPet = CosmeticRegistry.get(Cozmetics.id("angel_pet")).orElseThrow();
		assertEquals(CosmeticCategory.PET, angelPet.getCategory());
		assertEquals(AttachmentSlot.PET, angelPet.getAttachmentSlot());
		assertTrue(angelPet.isAnimated());
		assertEquals("animation.angelPet.idle", angelPet.getIdleAnimationName());

		Cosmetic wings = CosmeticRegistry.get(Cozmetics.id("bat_wings")).orElseThrow();
		assertEquals(CosmeticCategory.WINGS, wings.getCategory());
		assertEquals(AttachmentSlot.BODY, wings.getAttachmentSlot());
		assertTrue(wings.isAnimated());

		Cosmetic aura = CosmeticRegistry.get(Cozmetics.id("corrupted_aura")).orElseThrow();
		assertEquals(CosmeticCategory.AURA, aura.getCategory());
		assertEquals(AttachmentSlot.BODY, aura.getAttachmentSlot());
		assertTrue(aura.isAnimated());

		Cosmetic scythe = CosmeticRegistry.get(Cozmetics.id("medal_scythe")).orElseThrow();
		assertEquals(CosmeticCategory.BACK, scythe.getCategory());
		assertEquals(AttachmentSlot.BODY, scythe.getAttachmentSlot());
		assertTrue(scythe.isAnimated());

		Cosmetic boots = CosmeticRegistry.get(Cozmetics.id("gamer_rgb_boots")).orElseThrow();
		assertEquals(CosmeticCategory.BOOTS, boots.getCategory());
		assertEquals(AttachmentSlot.LEGS, boots.getAttachmentSlot());
		assertFalse(boots.isAnimated());

		Cosmetic mask = CosmeticRegistry.get(Cozmetics.id("crow_mask")).orElseThrow();
		assertEquals(CosmeticCategory.FACE, mask.getCategory());
		assertEquals(AttachmentSlot.HEAD, mask.getAttachmentSlot());
		assertFalse(mask.isAnimated());

		Cosmetic fists = CosmeticRegistry.get(Cozmetics.id("corrupted_fists")).orElseThrow();
		assertEquals(CosmeticCategory.FISTS, fists.getCategory());
		assertEquals(AttachmentSlot.ARMS, fists.getAttachmentSlot());
		assertFalse(fists.isAnimated());

		Cosmetic crown = CosmeticRegistry.get(Cozmetics.id("black_crown")).orElseThrow();
		assertEquals(CosmeticCategory.HEADWEAR, crown.getCategory());
		assertEquals(AttachmentSlot.HEAD, crown.getAttachmentSlot());
		assertFalse(crown.isAnimated());
		assertEquals(3.3f, crown.getTransform().translateY());
		assertTrue(crown.getTransform().staticLighting());
	}

	@Test
	void testCapeCategoryStrictlyDisabled() {
		assertFalse(CosmeticCategory.CAPE.isEnabled(), "Cape category must be strictly disabled");

		// Attempting to register a cosmetic in the CAPE category must throw
		Cosmetic fakeCape = CosmeticDefinition.builder(Cozmetics.id("fake_cape"), CosmeticCategory.CAPE, "Fake Cape")
				.model(Cozmetics.id("fake"))
				.texture(Cozmetics.id("fake"))
				.build();

		assertThrows(IllegalArgumentException.class, () -> CosmeticRegistry.register(fakeCape));
	}

	@Test
	void testAssetFilesIntegrity() throws Exception {
		Path resourceBase = Path.of("src/main/resources/assets");

		for (Cosmetic cosmetic : CosmeticRegistry.getAll()) {
			// 1. Verify model file exists
			Identifier modelId = cosmetic.getModelPath();
			Path modelFile = resourceBase.resolve(modelId.getNamespace())
					.resolve("geckolib/models")
					.resolve(modelId.getPath() + ".geo.json");
			assertTrue(Files.exists(modelFile), "Model file missing for " + cosmetic.getId() + " at " + modelFile);

			// Check model file is valid JSON with minecraft:geometry
			try (FileReader reader = new FileReader(modelFile.toFile())) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				assertTrue(json.has("minecraft:geometry") || json.has("format_version"),
						"Invalid geometry format in " + modelFile);
			}

			// 2. Verify texture file exists and is PNG
			Identifier textureId = cosmetic.getTexturePath();
			Path textureFile = resourceBase.resolve(textureId.getNamespace())
					.resolve(textureId.getPath());
			assertTrue(Files.exists(textureFile), "Texture file missing for " + cosmetic.getId() + " at " + textureFile);
			assertTrue(Files.size(textureFile) > 0, "Texture file empty for " + cosmetic.getId());

			// 3. If animated, verify animation file and idle animation key
			if (cosmetic.isAnimated()) {
				Identifier animId = cosmetic.getAnimationPath();
				assertNotNull(animId, "Animation path null for animated cosmetic " + cosmetic.getId());
				Path animFile = resourceBase.resolve(animId.getNamespace())
						.resolve("geckolib/animations")
						.resolve(animId.getPath() + ".animation.json");
				assertTrue(Files.exists(animFile), "Animation file missing for " + cosmetic.getId() + " at " + animFile);

				String idleKey = cosmetic.getIdleAnimationName();
				assertNotNull(idleKey, "Idle animation key null for " + cosmetic.getId());
				try (FileReader reader = new FileReader(animFile.toFile())) {
					JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
					JsonObject animations = json.getAsJsonObject("animations");
					assertNotNull(animations, "Missing animations block in " + animFile);
					assertTrue(animations.has(idleKey), "Idle animation '" + idleKey + "' not found in " + animFile);
				}
			}
		}
	}

	@Test
	void testCosmeticConfigDefaultsAndSerialization(@TempDir Path tempDir) {
		Path configFile = tempDir.resolve("cozmetics_test.json");

		// Test loading non-existent creates defaults
		CosmeticConfig config = CosmeticConfig.load(configFile);
		assertNotNull(config);
		assertEquals("cozmetics:angel_pet", config.equipped.get("PET"));
		assertTrue(config.visibility.get("PET"));
		assertTrue(config.visibility.get("WINGS"));
		assertNull(config.visibility.get("CAPE"), "Cape visibility should not be in default config");

		// Modify, save and reload
		config.equipped.put("WINGS", "cozmetics:bat_wings");
		config.visibility.put("PET", false);
		config.save(configFile);

		assertTrue(Files.exists(configFile));

		CosmeticConfig reloaded = CosmeticConfig.load(configFile);
		assertEquals("cozmetics:bat_wings", reloaded.equipped.get("WINGS"));
		assertEquals("cozmetics:angel_pet", reloaded.equipped.get("PET"));
		assertFalse(reloaded.visibility.get("PET"));
		assertTrue(reloaded.visibility.get("WINGS"));
	}
}

