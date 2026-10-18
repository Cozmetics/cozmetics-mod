package io.github.itsarcn.cozmetics.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.itsarcn.cozmetics.Cozmetics;
import io.github.itsarcn.cozmetics.api.CosmeticCatalogueProvider;
import io.github.itsarcn.cozmetics.api.LocalDevelopmentCatalogueProvider;
import io.github.itsarcn.cozmetics.api.LocalEquippedCosmeticsProvider;
import io.github.itsarcn.cozmetics.client.config.CosmeticConfig;
import io.github.itsarcn.cozmetics.cosmetic.*;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CosmeticsEngineTest {

	private static final String SAMPLE_FIXTURES_JSON = """
			{
			  "format_version": "1.0.0",
			  "cosmetics": [
			    {
			      "id": "cozmetics:sample_pet",
			      "name": "Sample Pet",
			      "category": "PET",
			      "model": "cozmetics:cosmetics/pets/sample_pet",
			      "texture": "cozmetics:textures/cosmetics/pets/sample_pet.png",
			      "animation": "cozmetics:cosmetics/pets/sample_pet",
			      "idle_animation": "animation.samplePet.idle",
			      "slot": "PET"
			    },
			    {
			      "id": "cozmetics:sample_boots",
			      "name": "Sample Boots",
			      "category": "BOOTS",
			      "model": "cozmetics:cosmetics/boots/sample_boots",
			      "texture": "cozmetics:textures/cosmetics/boots/sample_boots.png",
			      "texture_frames": 8,
			      "texture_frame_duration_ms": 100,
			      "slot": "LEGS"
			    },
			    {
			      "id": "cozmetics:sample_crown",
			      "name": "Sample Crown",
			      "category": "HEADWEAR",
			      "model": "cozmetics:cosmetics/headwear/sample_crown",
			      "texture": "cozmetics:textures/cosmetics/headwear/sample_crown.png",
			      "slot": "HEAD",
			      "transform": {
			        "translate_y": 0.0,
			        "scale": 1.0,
			        "static_lighting": true
			      }
			    }
			  ]
			}
			""";

	@BeforeEach
	void resetRegistry() {
		CosmeticRegistry.clear();
		CosmeticRegistry.init();
	}

	@Test
	void testDefaultEmptyState() {
		// In production/normal launch, the catalogue, registry, and equipped state must start empty
		Collection<Cosmetic> all = CosmeticRegistry.getAll();
		assertEquals(0, all.size(), "Production registry must start completely empty with 0 bundled demo cosmetics");

		CosmeticCatalogueProvider catalogue = LocalDevelopmentCatalogueProvider.getInstance();
		assertEquals(0, catalogue.getAllCosmetics().size(), "Default catalogue must be empty");

		LocalEquippedCosmeticsProvider equippedProvider = LocalEquippedCosmeticsProvider.getInstance();
		UUID testUuid = UUID.randomUUID();
		Map<CosmeticCategory, Cosmetic> equipped = equippedProvider.getEquippedCosmetics(testUuid);
		assertTrue(equipped.isEmpty(), "Player equipped map must start empty by default");

		CosmeticConfig defaultConfig = CosmeticConfig.createDefault();
		assertTrue(defaultConfig.equipped.isEmpty(), "Default configuration equipped map must be empty");
	}

	@Test
	void testExplicitOptInFixturesLoading() {
		int loaded = LocalDevelopmentCatalogueProvider.getInstance().loadFixtures(
				new ByteArrayInputStream(SAMPLE_FIXTURES_JSON.getBytes(StandardCharsets.UTF_8))
		);

		assertEquals(3, loaded, "Should have loaded exactly 3 sample fixtures");
		assertEquals(3, CosmeticRegistry.getAll().size());

		assertNotNull(CosmeticRegistry.get(Cozmetics.id("sample_pet")).orElse(null));
		assertNotNull(CosmeticRegistry.get(Cozmetics.id("sample_boots")).orElse(null));
		assertNotNull(CosmeticRegistry.get(Cozmetics.id("sample_crown")).orElse(null));

		Cosmetic boots = CosmeticRegistry.get(Cozmetics.id("sample_boots")).orElseThrow();
		assertEquals(CosmeticCategory.BOOTS, boots.getCategory());
		assertEquals(AttachmentSlot.LEGS, boots.getAttachmentSlot());
		assertEquals(8, boots.getTextureFrameCount());

		Cosmetic crown = CosmeticRegistry.get(Cozmetics.id("sample_crown")).orElseThrow();
		assertEquals(CosmeticCategory.HEADWEAR, crown.getCategory());
		assertEquals(AttachmentSlot.HEAD, crown.getAttachmentSlot());
		assertEquals(0.0f, crown.getTransform().translateY());
		assertTrue(crown.getTransform().staticLighting());
	}

	@Test
	void testDataDrivenJsonRoundTrip() {
		LocalDevelopmentCatalogueProvider.getInstance().loadFixtures(
				new ByteArrayInputStream(SAMPLE_FIXTURES_JSON.getBytes(StandardCharsets.UTF_8))
		);

		for (Cosmetic cosmetic : CosmeticRegistry.getAll()) {
			assertTrue(cosmetic instanceof CosmeticDefinition, "All fixtures should be CosmeticDefinition instances");
			CosmeticDefinition def = (CosmeticDefinition) cosmetic;
			JsonObject json = def.toJson();
			assertNotNull(json);

			CosmeticDefinition roundTripped = CosmeticDefinition.fromJson(json);
			assertEquals(def.getId(), roundTripped.getId());
			assertEquals(def.getCategory(), roundTripped.getCategory());
			assertEquals(def.getName(), roundTripped.getName());
			assertEquals(def.getModelPath(), roundTripped.getModelPath());
			assertEquals(def.getTexturePath(), roundTripped.getTexturePath());
			assertEquals(def.getTextureFrameCount(), roundTripped.getTextureFrameCount());
			assertEquals(def.getTextureFrameDurationMs(), roundTripped.getTextureFrameDurationMs());
			assertEquals(def.getAnimationPath(), roundTripped.getAnimationPath());
			assertEquals(def.getIdleAnimationName(), roundTripped.getIdleAnimationName());
			assertEquals(def.getAttachmentSlot(), roundTripped.getAttachmentSlot());
			assertEquals(def.getTransform().translateY(), roundTripped.getTransform().translateY());
			assertEquals(def.getTransform().scale(), roundTripped.getTransform().scale());
			assertEquals(def.getTransform().staticLighting(), roundTripped.getTransform().staticLighting());
		}
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
	void testLocalEquippedCosmeticsProvider() {
		LocalEquippedCosmeticsProvider provider = LocalEquippedCosmeticsProvider.getInstance();
		UUID testUuid = UUID.randomUUID();

		assertTrue(provider.getEquippedCosmetics(testUuid).isEmpty());

		Cosmetic fakeHat = CosmeticDefinition.builder(Cozmetics.id("test_hat"), CosmeticCategory.HEADWEAR, "Test Hat")
				.model(Cozmetics.id("fake_hat"))
				.texture(Cozmetics.id("fake_hat"))
				.build();

		provider.equip(testUuid, fakeHat);
		assertEquals(1, provider.getEquippedCosmetics(testUuid).size());
		assertEquals(fakeHat, provider.getEquipped(testUuid, CosmeticCategory.HEADWEAR).orElse(null));

		assertTrue(provider.isCosmeticVisible(testUuid, fakeHat.getId()));
		provider.setCosmeticVisible(testUuid, fakeHat.getId(), false);
		assertFalse(provider.isCosmeticVisible(testUuid, fakeHat.getId()));
		provider.setCosmeticVisible(testUuid, fakeHat.getId(), true);
		assertTrue(provider.isCosmeticVisible(testUuid, fakeHat.getId()));

		provider.unequip(testUuid, CosmeticCategory.HEADWEAR);
		assertTrue(provider.getEquippedCosmetics(testUuid).isEmpty());
	}

	@Test
	void testCosmeticConfigDefaultsAndSerialization(@TempDir Path tempDir) {
		Path configFile = tempDir.resolve("cozmetics_test.json");

		// Test loading non-existent creates defaults
		CosmeticConfig config = CosmeticConfig.load(configFile);
		assertNotNull(config);
		assertTrue(config.equipped.isEmpty(), "Default config equipped map must be empty");
		assertTrue(config.visibility.get("PET"));
		assertTrue(config.visibility.get("WINGS"));
		assertNull(config.visibility.get("CAPE"), "Cape visibility should not be in default config");

		// Modify, save and reload
		config.equipped.put("WINGS", "cozmetics:custom_wings");
		config.visibility.put("PET", false);
		config.save(configFile);

		assertTrue(Files.exists(configFile));

		CosmeticConfig reloaded = CosmeticConfig.load(configFile);
		assertEquals("cozmetics:custom_wings", reloaded.equipped.get("WINGS"));
		assertFalse(reloaded.visibility.get("PET"));
		assertTrue(reloaded.visibility.get("WINGS"));
	}

	@Test
	void testHiddenCosmeticsSeparateFromEquippedSerialization(@TempDir Path tempDir) {
		Path configFile = tempDir.resolve("cozmetics_hidden_test.json");

		CosmeticConfig config = CosmeticConfig.load(configFile);
		config.equipped.put("HEADWEAR", "cozmetics:custom_crown");
		config.hiddenCosmetics.add("cozmetics:custom_crown");
		config.save(configFile);

		CosmeticConfig reloaded = CosmeticConfig.load(configFile);
		// Equipped state remains untouched
		assertEquals("cozmetics:custom_crown", reloaded.equipped.get("HEADWEAR"));
		// Hidden state is persisted separately
		assertTrue(reloaded.hiddenCosmetics.contains("cozmetics:custom_crown"));

		// Showing it removes from hiddenCosmetics, equipped remains unchanged
		reloaded.hiddenCosmetics.remove("cozmetics:custom_crown");
		reloaded.save(configFile);

		CosmeticConfig reloadedAfterShow = CosmeticConfig.load(configFile);
		assertEquals("cozmetics:custom_crown", reloadedAfterShow.equipped.get("HEADWEAR"));
		assertFalse(reloadedAfterShow.hiddenCosmetics.contains("cozmetics:custom_crown"));
	}

	@Test
	void testOptionalModMenuEntrypointConfigured() throws Exception {
		Path modJsonPath = Path.of("src/main/resources/fabric.mod.json");
		assertTrue(Files.exists(modJsonPath));

		try (FileReader reader = new FileReader(modJsonPath.toFile())) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			JsonObject entrypoints = json.getAsJsonObject("entrypoints");
			assertNotNull(entrypoints);
			assertTrue(entrypoints.has("modmenu"), "ModMenu entrypoint must be declared in fabric.mod.json");

			String modMenuClass = entrypoints.getAsJsonArray("modmenu").get(0).getAsString();
			assertEquals("io.github.itsarcn.cozmetics.client.compat.modmenu.CozmeticsModMenuImpl", modMenuClass);

			// Ensure ModMenu is NOT a hard dependency in "depends"
			JsonObject depends = json.getAsJsonObject("depends");
			assertFalse(depends.has("modmenu"), "ModMenu must NOT be a hard dependency in 'depends'");
		}
	}
}
