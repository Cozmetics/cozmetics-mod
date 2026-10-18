package io.github.itsarcn.cozmetics.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.itsarcn.cozmetics.bbmodel.*;
import io.github.itsarcn.cozmetics.client.texture.ImageFormat;
import io.github.itsarcn.cozmetics.cosmetic.ModelValidator;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class BbModelTest {

	private static final String SAMPLE_BBMODEL_JSON = """
			{
			  "meta": {
			    "format_version": "4.10",
			    "model_format": "bedrock",
			    "box_uv": true
			  },
			  "name": "sample_cosmetic",
			  "resolution": {
			    "width": 64,
			    "height": 64
			  },
			  "elements": [
			    {
			      "name": "crown_base",
			      "box_uv": true,
			      "rescale": false,
			      "from": [-4, 24, -4],
			      "to": [4, 30, 4],
			      "origin": [0, 24, 0],
			      "rotation": [0, 0, 0],
			      "uv_offset": [0, 0],
			      "color": 0,
			      "uuid": "36d606d8-1baa-08f0-e4e6-f70f2f2e3657",
			      "faces": {
			        "north": {"uv": [8, 8, 16, 14], "texture": 0},
			        "east": {"uv": [0, 8, 8, 14], "texture": 0},
			        "south": {"uv": [24, 8, 32, 14], "texture": 0},
			        "west": {"uv": [16, 8, 24, 14], "texture": 0},
			        "up": {"uv": [8, 0, 16, 8], "texture": 0},
			        "down": {"uv": [16, 0, 24, 8], "texture": 0}
			      }
			    },
			    {
			      "name": "gem",
			      "box_uv": true,
			      "rescale": false,
			      "from": [-1, 30, -5],
			      "to": [1, 32, -3],
			      "origin": [0, 30, -4],
			      "rotation": [0, 0, 0],
			      "uv_offset": [32, 0],
			      "color": 1,
			      "uuid": "7594f374-dece-aa7b-e553-4dca3c243b1f",
			      "faces": {
			        "north": {"uv": [34, 2, 36, 4], "texture": 0},
			        "east": {"uv": [32, 2, 34, 4], "texture": 0},
			        "south": {"uv": [38, 2, 40, 4], "texture": 0},
			        "west": {"uv": [36, 2, 38, 4], "texture": 0},
			        "up": {"uv": [34, 0, 36, 2], "texture": 0},
			        "down": {"uv": [36, 0, 38, 2], "texture": 0}
			      }
			    }
			  ],
			  "outliner": [
			    {
			      "name": "anchor_head",
			      "origin": [0, 24, 0],
			      "rotation": [0, 0, 0],
			      "uuid": "b147a4cf-3f58-9e94-8f4c-b7b0657c3c56",
			      "children": [
			        "36d606d8-1baa-08f0-e4e6-f70f2f2e3657",
			        "7594f374-dece-aa7b-e553-4dca3c243b1f"
			      ]
			    }
			  ],
			  "textures": [
			    {
			      "path": "",
			      "name": "sample_texture.png",
			      "folder": "",
			      "namespace": "",
			      "id": "0",
			      "particle": false,
			      "render_mode": "default",
			      "visible": true,
			      "mode": "bitmap",
			      "saved": true,
			      "uuid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
			      "source": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAA0SURBVHhe7cEBDQAAAMKg909tDwcUAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA4DMDy+4AAe7x4Q4AAAAASUVORK5CYII="
			    }
			  ],
			  "animations": [
			    {
			      "name": "animation.sample.idle",
			      "uuid": "f1e2d3c4-b5a6-7890-1234-56789abcdef0",
			      "loop": "loop",
			      "override": false,
			      "length": 2.0,
			      "snapping": 24,
			      "animators": {
			        "b147a4cf-3f58-9e94-8f4c-b7b0657c3c56": {
			          "name": "anchor_head",
			          "type": "bone",
			          "keyframes": [
			            {
			              "channel": "rotation",
			              "data_points": [{"x": 0.0, "y": 0.0, "z": 0.0}],
			              "time": 0.0,
			              "interpolation": "linear"
			            },
			            {
			              "channel": "rotation",
			              "data_points": [{"x": 0.0, "y": 15.0, "z": 0.0}],
			              "time": 1.0,
			              "interpolation": "linear"
			            },
			            {
			              "channel": "rotation",
			              "data_points": [{"x": 0.0, "y": 0.0, "z": 0.0}],
			              "time": 2.0,
			              "interpolation": "linear"
			            },
			            {
			              "channel": "position",
			              "data_points": [{"x": 0.0, "y": 0.0, "z": 0.0}],
			              "time": 0.0,
			              "interpolation": "linear"
			            },
			            {
			              "channel": "position",
			              "data_points": [{"x": 0.0, "y": 1.5, "z": 0.0}],
			              "time": 1.0,
			              "interpolation": "linear"
			            },
			            {
			              "channel": "position",
			              "data_points": [{"x": 0.0, "y": 0.0, "z": 0.0}],
			              "time": 2.0,
			              "interpolation": "linear"
			            }
			          ]
			        }
			      }
			    }
			  ]
			}
			""";

	@Test
	void testParseBbModel() {
		BbModel model = BbModelParser.parse(SAMPLE_BBMODEL_JSON);

		assertNotNull(model);
		assertEquals("sample_cosmetic", model.getName());
		assertEquals("4.10", model.getFormatVersion());
		assertEquals("bedrock", model.getModelFormat());
		assertTrue(model.isBoxUv());
		assertEquals(64, model.getResolutionWidth());
		assertEquals(64, model.getResolutionHeight());

		// Bones
		BbModel.BbBone headBone = model.getBone("anchor_head");
		assertNotNull(headBone, "anchor_head bone should be present");
		assertEquals(2, headBone.getCubes().size(), "anchor_head should contain 2 cubes");

		BbModel.BbCube crown = headBone.getCubes().get(0);
		assertEquals("crown_base", crown.getName());
		assertEquals(-4.0f, crown.getFrom()[0]);
		assertEquals(24.0f, crown.getFrom()[1]);
		assertEquals(4.0f, crown.getTo()[0]);
		assertEquals(30.0f, crown.getTo()[1]);

		BbModel.BbCube gem = headBone.getCubes().get(1);
		assertEquals("gem", gem.getName());

		// Textures
		assertEquals(1, model.getTextures().size());
		BbModel.BbTexture tex = model.getTextures().get(0);
		assertEquals("sample_texture.png", tex.getName());
		assertTrue(tex.hasEmbeddedData());
		byte[] decoded = tex.decodeEmbeddedBytes();
		assertNotNull(decoded);
		assertTrue(decoded.length > 0);
		assertEquals(ImageFormat.PNG, ImageFormat.detect(decoded));

		// Animations
		assertEquals(1, model.getAnimations().size());
		BbModel.BbAnimation anim = model.getAnimation("animation.sample.idle");
		assertNotNull(anim);
		assertEquals(2.0f, anim.getLength());
		assertEquals("loop", anim.getLoop());

		BbModel.BbBoneAnimator animator = anim.getAnimators().get("anchor_head");
		assertNotNull(animator);
		assertEquals(3, animator.getRotationKeyframes().size());
		assertEquals(3, animator.getPositionKeyframes().size());
		assertEquals(15.0f, animator.getRotationKeyframes().get(1).vector()[1]);
		assertEquals(1.5f, animator.getPositionKeyframes().get(1).vector()[1]);
	}

	@Test
	void testAdaptToBedrockGeometryAndValidate() {
		BbModel model = BbModelParser.parse(SAMPLE_BBMODEL_JSON);

		JsonObject geoJson = BbModelAdapter.toBedrockGeometry(model);
		assertNotNull(geoJson);
		assertEquals("1.12.0", geoJson.get("format_version").getAsString());
		assertTrue(geoJson.has("minecraft:geometry"));

		JsonArray geoms = geoJson.getAsJsonArray("minecraft:geometry");
		assertEquals(1, geoms.size());
		JsonObject geom = geoms.get(0).getAsJsonObject();
		assertEquals("geometry.sample_cosmetic", geom.getAsJsonObject("description").get("identifier").getAsString());

		// Verify with ModelValidator
		byte[] jsonBytes = geoJson.toString().getBytes(StandardCharsets.UTF_8);
		try (InputStream in = new ByteArrayInputStream(jsonBytes)) {
			ModelValidator.ValidationResult res = ModelValidator.validateGeometry(in, "adapted_geometry.json");
			assertTrue(res.valid(), "Adapted geometry should be valid: " + res.message());
			assertTrue(res.bones().contains("anchor_head"), "Bones should contain anchor_head");
		} catch (Exception e) {
			fail(e);
		}
	}

	@Test
	void testAdaptToBedrockAnimationsAndValidate() {
		BbModel model = BbModelParser.parse(SAMPLE_BBMODEL_JSON);

		JsonObject animJson = BbModelAdapter.toBedrockAnimations(model);
		assertNotNull(animJson);
		assertEquals("1.8.0", animJson.get("format_version").getAsString());
		assertTrue(animJson.has("animations"));

		JsonObject anims = animJson.getAsJsonObject("animations");
		assertTrue(anims.has("animation.sample.idle"));
		JsonObject sampleIdle = anims.getAsJsonObject("animation.sample.idle");
		assertEquals(2.0f, sampleIdle.get("animation_length").getAsFloat());
		assertTrue(sampleIdle.get("loop").getAsBoolean());

		// Verify with ModelValidator
		byte[] jsonBytes = animJson.toString().getBytes(StandardCharsets.UTF_8);
		try (InputStream in = new ByteArrayInputStream(jsonBytes)) {
			boolean valid = ModelValidator.validateAnimation(in, "animation.sample.idle", "adapted_animations.json");
			assertTrue(valid, "Adapted animation should be valid");
		} catch (Exception e) {
			fail(e);
		}
	}

	@Test
	void testBbModelManager() {
		Identifier testId = Identifier.of("cozmetics", "sample_cosmetic");

		BbModelManager manager = BbModelManager.getInstance();
		manager.clear();
		assertFalse(manager.hasModel(testId));

		try (InputStream in = new ByteArrayInputStream(SAMPLE_BBMODEL_JSON.getBytes(StandardCharsets.UTF_8))) {
			var loaded = manager.load(testId, in);
			assertTrue(loaded.isPresent());
		} catch (Exception e) {
			fail(e);
		}

		assertTrue(manager.hasModel(testId));
		assertNotNull(manager.get(testId).orElse(null));
		assertNotNull(manager.getGeometryJson(testId));
		assertNotNull(manager.getAnimationJson(testId));

		manager.clear();
		assertFalse(manager.hasModel(testId));
	}

	@Test
	void testBbModelConverter(@TempDir Path tempDir) throws Exception {
		Path inputBbmodel = tempDir.resolve("sample_cosmetic.bbmodel");
		Files.writeString(inputBbmodel, SAMPLE_BBMODEL_JSON);

		Path outputDir = tempDir.resolve("output");
		BbModelConverter.ConversionResult result = BbModelConverter.convert(inputBbmodel, outputDir);

		assertTrue(result.success());
		assertEquals("sample_cosmetic", result.modelName());
		assertTrue(Files.exists(result.geometryPath()));
		assertTrue(Files.exists(result.animationPath()));
		assertEquals(1, result.texturePaths().size());

		// Verify extracted texture is a valid PNG
		Path texFile = result.texturePaths().values().iterator().next();
		assertTrue(Files.exists(texFile));
		byte[] texBytes = Files.readAllBytes(texFile);
		assertEquals(ImageFormat.PNG, ImageFormat.detect(texBytes));
	}
}
