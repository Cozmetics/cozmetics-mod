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

	@Test
	void testCosmeticModelBoundsForAnyCosmetic() {
		// Null safety
		var defaultBounds = io.github.itsarcn.cozmetics.client.render.CosmeticModelBounds.get(null);
		assertNotNull(defaultBounds);
		assertEquals(1.0f, defaultBounds.maxSpan());

		// Test Wings dynamic slot configuration
		Cosmetic wings = new CosmeticDefinition.Builder(
				net.minecraft.util.Identifier.of("cozmetics:test_wings"),
				CosmeticCategory.WINGS,
				"Dragon Wings"
		).model(net.minecraft.util.Identifier.of("cozmetics:geo_wings"))
		 .texture(net.minecraft.util.Identifier.of("cozmetics:tex_wings"))
		 .build();

		var wingBounds = io.github.itsarcn.cozmetics.client.render.CosmeticModelBounds.get(wings);
		assertNotNull(wingBounds);
		assertTrue(wingBounds.maxSpan() >= 2.0f, "Wings must have wide span (>= 2.0f) so they scale properly");
		assertEquals(0.0f, wingBounds.centerX(), 0.001f, "Wings must be horizontally centered");

		// Test Pet dynamic slot configuration
		Cosmetic pet = new CosmeticDefinition.Builder(
				net.minecraft.util.Identifier.of("cozmetics:test_pet"),
				CosmeticCategory.PET,
				"Baby Dragon Pet"
		).model(net.minecraft.util.Identifier.of("cozmetics:geo_pet"))
		 .texture(net.minecraft.util.Identifier.of("cozmetics:tex_pet"))
		 .build();

		var petBounds = io.github.itsarcn.cozmetics.client.render.CosmeticModelBounds.get(pet);
		assertNotNull(petBounds);
		assertTrue(petBounds.centerX() > 0.0f, "Pet must have positive GeckoLib model centerX to offset shoulder displacement");
		assertTrue(petBounds.centerY() > 1.5f, "Pet center Y must be hovering above torso");

		// Test Arms (fists)
		Cosmetic fists = new CosmeticDefinition.Builder(
				net.minecraft.util.Identifier.of("cozmetics:test_fists"),
				CosmeticCategory.FISTS,
				"RGB Fists"
		).model(net.minecraft.util.Identifier.of("cozmetics:geo_fists"))
		 .texture(net.minecraft.util.Identifier.of("cozmetics:tex_fists"))
		 .build();

		var fistBounds = io.github.itsarcn.cozmetics.client.render.CosmeticModelBounds.get(fists);
		assertNotNull(fistBounds);
		assertEquals(0.0f, fistBounds.centerX(), 0.001f);
	}

	@Test
	void testFromBakedModelCalculation() {
		// Create a mock model: Cube from (-1.5, 1.0, -0.5) to (1.5, 2.0, 0.5)
		software.bernie.geckolib.cache.model.GeoVertex v1 = new software.bernie.geckolib.cache.model.GeoVertex(-1.5f, 1.0f, -0.5f, 0, 0);
		software.bernie.geckolib.cache.model.GeoVertex v2 = new software.bernie.geckolib.cache.model.GeoVertex(1.5f, 2.0f, 0.5f, 1, 1);
		software.bernie.geckolib.cache.model.GeoQuad quad = new software.bernie.geckolib.cache.model.GeoQuad(
				new software.bernie.geckolib.cache.model.GeoVertex[]{v1, v2}, 0, 1, 0, net.minecraft.util.math.Direction.UP);
		software.bernie.geckolib.cache.model.cuboid.GeoCube cube = new software.bernie.geckolib.cache.model.cuboid.GeoCube(
				new software.bernie.geckolib.cache.model.GeoQuad[]{quad}, net.minecraft.util.math.Vec3d.ZERO, net.minecraft.util.math.Vec3d.ZERO, net.minecraft.util.math.Vec3d.ZERO);
		software.bernie.geckolib.cache.model.cuboid.CuboidGeoBone bone = new software.bernie.geckolib.cache.model.cuboid.CuboidGeoBone(
				null, "anchor_body", new software.bernie.geckolib.cache.model.GeoBone[0],
				new software.bernie.geckolib.cache.model.cuboid.GeoCube[]{cube}, 0, 0, 0, 0, 0, 0);
		software.bernie.geckolib.cache.model.BakedGeoModel bakedModel = new software.bernie.geckolib.cache.model.BakedGeoModel(
				new software.bernie.geckolib.cache.model.GeoBone[]{bone}, null);

		var bounds = io.github.itsarcn.cozmetics.client.render.CosmeticModelBounds.fromBakedModel(bakedModel);
		assertNotNull(bounds);
		assertEquals(0.0f, bounds.centerX(), 0.001f);
		assertEquals(1.5f, bounds.centerY(), 0.001f);
		assertEquals(0.0f, bounds.centerZ(), 0.001f);
		assertEquals(3.0f, bounds.spanX(), 0.001f);
		assertEquals(1.0f, bounds.spanY(), 0.001f);
		assertEquals(1.0f, bounds.spanZ(), 0.001f);
		assertEquals(3.0f, bounds.maxSpan(), 0.001f);
		assertFalse(bounds.isDefault());
	}
}
