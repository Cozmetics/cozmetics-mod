package io.github.itsarcn.cozmetics.test;

import com.google.gson.JsonObject;
import io.github.itsarcn.cozmetics.cache.CosmeticCacheManager;
import io.github.itsarcn.cozmetics.catalog.CatalogItem;
import io.github.itsarcn.cozmetics.client.cosmetic.PlayerCosmeticTracker;
import io.github.itsarcn.cozmetics.client.pack.CosmeticPackageLoader;
import io.github.itsarcn.cozmetics.cosmetic.*;
import io.github.itsarcn.cozmetics.pack.CosmeticPackageManifest;
import io.github.itsarcn.cozmetics.pack.CosmeticPackageValidator;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ZipPackagePipelineTest {

	private static final String BASE64_PNG =
			"iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAA0SURBVHhe7cEBDQAAAMKg909tDwcUAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA4DMDy+4AAe7x4Q4AAAAASUVORK5CYII=";

	private static final String SAMPLE_BBMODEL = """
			{
			  "meta": {"format_version": "4.10", "model_format": "bedrock", "box_uv": true},
			  "name": "test_wings",
			  "resolution": {"width": 64, "height": 64},
			  "elements": [
			    {
			      "name": "wing_left",
			      "box_uv": true,
			      "from": [-8, 12, 2],
			      "to": [0, 20, 3],
			      "origin": [0, 16, 2],
			      "uuid": "11111111-2222-3333-4444-555555555555",
			      "faces": {
			        "north": {"uv": [0, 0, 8, 8], "texture": 0},
			        "south": {"uv": [8, 0, 16, 8], "texture": 0}
			      }
			    }
			  ],
			  "outliner": [
			    {
			      "name": "anchor_body",
			      "origin": [0, 16, 0],
			      "uuid": "99999999-8888-7777-6666-555555555555",
			      "children": ["11111111-2222-3333-4444-555555555555"]
			    }
			  ],
			  "textures": [
			    {
			      "name": "wings.png",
			      "uuid": "77777777-6666-5555-4444-333333333333",
			      "source": "data:image/png;base64,%s"
			    }
			  ]
			}
			""".formatted(BASE64_PNG);

	@BeforeEach
	void resetRegistry() {
		CosmeticRegistry.clear();
	}

	private byte[] createTestZip(String manifestJson, String modelName, String modelContent, String textureName, byte[] textureBytes) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ZipOutputStream zos = new ZipOutputStream(baos)) {
			// Manifest
			zos.putNextEntry(new ZipEntry("manifest.json"));
			zos.write(manifestJson.getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();

			// Model
			zos.putNextEntry(new ZipEntry(modelName));
			zos.write(modelContent.getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();

			// Texture
			zos.putNextEntry(new ZipEntry(textureName));
			zos.write(textureBytes);
			zos.closeEntry();
		}
		return baos.toByteArray();
	}

	@Test
	void testManifestParsingAndSerialization() {
		String manifestJson = """
				{
				  "id": "cozmetics:blaze_wings",
				  "version": "1.2.0",
				  "model": "models/wings.bbmodel",
				  "texture": "textures/wings.png",
				  "slot": "BODY",
				  "idle_animation": "animation.wings.idle",
				  "transform": {
				    "translate_y": 0.2,
				    "scale": 1.1,
				    "static_lighting": true
				  }
				}
				""";

		CosmeticPackageManifest manifest = CosmeticPackageManifest.parse(manifestJson);
		assertNotNull(manifest);
		assertEquals(Identifier.of("cozmetics", "blaze_wings"), manifest.id());
		assertEquals("1.2.0", manifest.version());
		assertEquals("models/wings.bbmodel", manifest.modelPath());
		assertEquals("textures/wings.png", manifest.texturePath());
		assertEquals(AttachmentSlot.BODY, manifest.slot());
		assertEquals("animation.wings.idle", manifest.idleAnimationName());
		assertEquals(0.2f, manifest.transform().translateY());
		assertEquals(1.1f, manifest.transform().scale());
		assertTrue(manifest.transform().staticLighting());

		// Round-trip
		JsonObject serialized = manifest.toJson();
		CosmeticPackageManifest roundTripped = CosmeticPackageManifest.parseJson(serialized);
		assertEquals(manifest.id(), roundTripped.id());
		assertEquals(manifest.version(), roundTripped.version());
		assertEquals(manifest.modelPath(), roundTripped.modelPath());
		assertEquals(manifest.texturePath(), roundTripped.texturePath());
		assertEquals(manifest.slot(), roundTripped.slot());
	}

	@Test
	void testPackageValidatorValidPackage(@TempDir Path tempDir) throws Exception {
		String manifestJson = """
				{
				  "id": "cozmetics:test_wings",
				  "version": "1.0.0",
				  "model": "model.bbmodel",
				  "texture": "texture.png"
				}
				""";
		byte[] pngBytes = Base64.getDecoder().decode(BASE64_PNG);
		byte[] zipBytes = createTestZip(manifestJson, "model.bbmodel", SAMPLE_BBMODEL, "texture.png", pngBytes);

		Path zipFile = tempDir.resolve("valid_package.zip");
		Files.write(zipFile, zipBytes);

		String expectedHash = CosmeticPackageValidator.computeSha256(zipFile);

		var result = CosmeticPackageValidator.validatePackage(zipFile, expectedHash);
		assertTrue(result.valid(), "Valid package should pass validation: " + result.message());
	}

	@Test
	void testPackageValidatorRejectsHashMismatch(@TempDir Path tempDir) throws Exception {
		String manifestJson = """
				{
				  "id": "cozmetics:test_wings",
				  "version": "1.0.0",
				  "model": "model.bbmodel",
				  "texture": "texture.png"
				}
				""";
		byte[] zipBytes = createTestZip(manifestJson, "model.bbmodel", SAMPLE_BBMODEL, "texture.png", new byte[]{0});
		Path zipFile = tempDir.resolve("test.zip");
		Files.write(zipFile, zipBytes);

		var result = CosmeticPackageValidator.validatePackage(zipFile, "0000000000000000000000000000000000000000000000000000000000000000");
		assertFalse(result.valid(), "Should fail on SHA-256 mismatch");
		assertTrue(result.message().contains("hash mismatch"));
	}

	@Test
	void testPackageValidatorRejectsZipSlipPathTraversal(@TempDir Path tempDir) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ZipOutputStream zos = new ZipOutputStream(baos)) {
			zos.putNextEntry(new ZipEntry("manifest.json"));
			zos.write("{}".getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();

			// Malicious entry with path traversal
			zos.putNextEntry(new ZipEntry("../evil.json"));
			zos.write("evil".getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
		}

		Path maliciousZip = tempDir.resolve("malicious.zip");
		Files.write(maliciousZip, baos.toByteArray());

		var result = CosmeticPackageValidator.validatePackage(maliciousZip, null);
		assertFalse(result.valid(), "Should reject package with path traversal");
		assertTrue(result.message().contains("Malicious path"));
	}

	@Test
	void testPackageValidatorRejectsDisallowedFileExtension(@TempDir Path tempDir) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ZipOutputStream zos = new ZipOutputStream(baos)) {
			zos.putNextEntry(new ZipEntry("manifest.json"));
			zos.write("{}".getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();

			// Disallowed executable extension
			zos.putNextEntry(new ZipEntry("payload.exe"));
			zos.write(new byte[]{0, 1, 2});
			zos.closeEntry();
		}

		Path zipFile = tempDir.resolve("payload.zip");
		Files.write(zipFile, baos.toByteArray());

		var result = CosmeticPackageValidator.validatePackage(zipFile, null);
		assertFalse(result.valid(), "Should reject package containing .exe");
		assertTrue(result.message().contains("Disallowed file type"));
	}

	@Test
	void testPackageValidatorRejectsMissingManifest(@TempDir Path tempDir) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ZipOutputStream zos = new ZipOutputStream(baos)) {
			zos.putNextEntry(new ZipEntry("model.bbmodel"));
			zos.write(SAMPLE_BBMODEL.getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
		}

		Path zipFile = tempDir.resolve("no_manifest.zip");
		Files.write(zipFile, baos.toByteArray());

		var result = CosmeticPackageValidator.validatePackage(zipFile, null);
		assertFalse(result.valid(), "Should reject package missing manifest.json");
		assertTrue(result.message().contains("Missing required 'manifest.json'"));
	}

	@Test
	void testExtractAndLoadPackage(@TempDir Path tempDir) throws Exception {
		String manifestJson = """
				{
				  "id": "cozmetics:blaze_wings",
				  "version": "1.0.0",
				  "model": "wings.bbmodel",
				  "texture": "wings.png",
				  "slot": "BODY",
				  "transform": {
				    "translate_y": 0.0,
				    "scale": 1.0,
				    "static_lighting": false
				  }
				}
				""";
		byte[] pngBytes = Base64.getDecoder().decode(BASE64_PNG);
		byte[] zipBytes = createTestZip(manifestJson, "wings.bbmodel", SAMPLE_BBMODEL, "wings.png", pngBytes);

		Path zipFile = tempDir.resolve("blaze_wings.zip");
		Files.write(zipFile, zipBytes);
		String hash = CosmeticPackageValidator.computeSha256(zipFile);

		Path extractedDir = tempDir.resolve("extracted");
		Cosmetic loaded = CosmeticPackageLoader.loadPackage(
				zipFile,
				extractedDir,
				"Blaze Wings",
				CosmeticCategory.WINGS,
				hash
		);

		assertNotNull(loaded);
		assertEquals(Identifier.of("cozmetics", "blaze_wings"), loaded.getId());
		assertEquals("Blaze Wings", loaded.getName());
		assertEquals(CosmeticCategory.WINGS, loaded.getCategory());
		assertEquals(AttachmentSlot.BODY, loaded.getAttachmentSlot());

		// Verify registry registration
		assertTrue(CosmeticRegistry.get(loaded.getId()).isPresent());
	}

	@Test
	void testMetadataDecouplingAllowsNameAndCategoryUpdatesWithoutRedownloading(@TempDir Path tempDir) throws Exception {
		String manifestJson = """
				{
				  "id": "cozmetics:custom_hat",
				  "version": "1.0.0",
				  "model": "hat.bbmodel",
				  "texture": "hat.png",
				  "slot": "HEAD"
				}
				""";
		byte[] pngBytes = Base64.getDecoder().decode(BASE64_PNG);
		byte[] zipBytes = createTestZip(manifestJson, "hat.bbmodel", SAMPLE_BBMODEL, "hat.png", pngBytes);

		Path zipFile = tempDir.resolve("hat.zip");
		Files.write(zipFile, zipBytes);
		Path extractedDir = tempDir.resolve("hat_extracted");

		// Initial load
		CosmeticPackageLoader.loadPackage(zipFile, extractedDir, "Old Hat Name", CosmeticCategory.HEADWEAR, null);
		assertEquals("Old Hat Name", CosmeticRegistry.get(Identifier.of("cozmetics", "custom_hat")).orElseThrow().getName());

		// Updating catalogue metadata (name changed to "Royal Crown", category changed to FACE)
		// without modifying the ZIP or re-extracting:
		Cosmetic updated = CosmeticPackageLoader.loadFromExtracted(extractedDir, "Royal Crown", CosmeticCategory.FACE);
		assertEquals("Royal Crown", updated.getName());
		assertEquals(CosmeticCategory.FACE, updated.getCategory());
		assertEquals(updated, CosmeticRegistry.get(Identifier.of("cozmetics", "custom_hat")).orElseThrow());
	}

	@Test
	void testCacheManagerOperationsAndValidation(@TempDir Path tempDir) throws Exception {
		CosmeticCacheManager cache = new CosmeticCacheManager(tempDir.resolve("cache"));
		Identifier testId = Identifier.of("cozmetics", "sample_aura");

		assertFalse(cache.isPackageCachedAndValid(testId, "1.0.0", null));

		// Create extracted directory with manifest
		Path extracted = cache.getExtractedPackageDir(testId);
		Files.createDirectories(extracted);
		Files.writeString(extracted.resolve("manifest.json"), "{\"id\":\"cozmetics:sample_aura\",\"version\":\"1.0.0\"}");

		// Now valid for 1.0.0
		assertTrue(cache.isPackageCachedAndValid(testId, "1.0.0", null));

		// Invalidation on version mismatch
		assertFalse(cache.isPackageCachedAndValid(testId, "2.0.0", null));

		// Clean extracted
		cache.cleanExtracted(testId);
		assertFalse(Files.exists(extracted.resolve("manifest.json")));
		assertFalse(cache.isPackageCachedAndValid(testId, "1.0.0", null));
	}

	@Test
	void testPlayerCosmeticTrackerMultiplayerMapping() {
		PlayerCosmeticTracker tracker = PlayerCosmeticTracker.getInstance();
		tracker.clearAll();

		UUID playerA = UUID.randomUUID();
		UUID playerB = UUID.randomUUID();

		Identifier wingsId = Identifier.of("cozmetics", "phoenix_wings");
		Identifier crownId = Identifier.of("cozmetics", "golden_crown");

		tracker.setPlayerEquipped(playerA, CosmeticCategory.WINGS, wingsId);
		tracker.setPlayerEquipped(playerB, CosmeticCategory.HEADWEAR, crownId);

		// If cosmetic is in registry, getEquipped returns it
		Cosmetic fakeCrown = CosmeticDefinition.builder(crownId, CosmeticCategory.HEADWEAR, "Golden Crown")
				.model(crownId)
				.texture(crownId)
				.build();
		CosmeticRegistry.register(fakeCrown);

		Cosmetic equippedB = tracker.getEquipped(playerB, CosmeticCategory.HEADWEAR);
		assertNotNull(equippedB);
		assertEquals("Golden Crown", equippedB.getName());

		// Disconnect player B
		tracker.clearPlayer(playerB);
		assertNull(tracker.getEquipped(playerB, CosmeticCategory.HEADWEAR));

		tracker.clearAll();
	}

	@Test
	void testRealCosmeticsPackagesValidationAndLoading(@TempDir Path tempDir) throws Exception {
		Path runPackagesDir = Path.of("run/cozmetics/cache/packages");
		Path runMetadataFile = Path.of("run/cozmetics/cache/metadata/catalogue.json");

		assertTrue(Files.exists(runMetadataFile), "catalogue.json must exist in run cache");

		String jsonStr = Files.readString(runMetadataFile, StandardCharsets.UTF_8);
		com.google.gson.JsonArray array = com.google.gson.JsonParser.parseString(jsonStr).getAsJsonArray();
		assertEquals(4, array.size(), "Should have exactly 4 items in catalogue.json");

		for (com.google.gson.JsonElement elem : array) {
			CatalogItem item = CatalogItem.fromJson(elem.getAsJsonObject());
			Path zipPath = runPackagesDir.resolve(CosmeticCacheManager.sanitizeIdentifier(item.id()) + ".zip");
			assertTrue(Files.exists(zipPath), "ZIP package must exist: " + zipPath);

			// 1. Test real package validation
			var valResult = CosmeticPackageValidator.validatePackage(zipPath, item.hash());
			assertTrue(valResult.valid(), "Package " + item.id() + " must pass validation: " + valResult.message());

			// 2. Test extraction and loading
			Path extractTarget = tempDir.resolve("extracted_" + CosmeticCacheManager.sanitizeIdentifier(item.id()));
			Cosmetic loaded = CosmeticPackageLoader.loadPackage(
					zipPath,
					extractTarget,
					item.name(),
					item.category(),
					item.hash()
			);

			assertNotNull(loaded);
			assertEquals(item.id(), loaded.getId());
			assertEquals(item.name(), loaded.getName());
			assertEquals(item.category(), loaded.getCategory());
			assertTrue(CosmeticRegistry.get(loaded.getId()).isPresent());
		}
	}
}
