package io.github.itsarcn.cozmetics.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.itsarcn.cozmetics.Cozmetics;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Validator and diagnostic inspector for Bedrock geometry (.geo.json) and animation (.animation.json) files.
 * Provides diagnostics for missing bones, malformed geometry, and unresolvable animation references.
 */
public final class ModelValidator {
	private ModelValidator() {}

	public record ValidationResult(boolean valid, String message, Set<String> bones) {}

	/**
	 * Validates a Bedrock geometry JSON stream.
	 */
	public static ValidationResult validateGeometry(InputStream stream, String sourceName) {
		try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

			JsonArray geometries = null;
			if (root.has("minecraft:geometry")) {
				JsonElement elem = root.get("minecraft:geometry");
				if (elem.isJsonArray()) {
					geometries = elem.getAsJsonArray();
				}
			}

			if (geometries == null || geometries.isEmpty()) {
				// Legacy format check
				for (String key : root.keySet()) {
					if (key.startsWith("geometry.")) {
						JsonObject geomObj = root.getAsJsonObject(key);
						return inspectBones(geomObj.getAsJsonArray("bones"), sourceName);
					}
				}
				return new ValidationResult(false, "No 'minecraft:geometry' array or legacy geometry found in " + sourceName, Set.of());
			}

			JsonObject firstGeom = geometries.get(0).getAsJsonObject();
			if (!firstGeom.has("bones")) {
				return new ValidationResult(false, "Geometry has no 'bones' array in " + sourceName, Set.of());
			}

			return inspectBones(firstGeom.getAsJsonArray("bones"), sourceName);
		} catch (Exception e) {
			Cozmetics.LOGGER.error("Failed to parse geometry JSON '{}': {}", sourceName, e.getMessage());
			return new ValidationResult(false, "JSON parsing error: " + e.getMessage(), Set.of());
		}
	}

	private static ValidationResult inspectBones(JsonArray bonesArray, String sourceName) {
		if (bonesArray == null || bonesArray.isEmpty()) {
			return new ValidationResult(false, "Bones array is empty in " + sourceName, Set.of());
		}

		Set<String> boneNames = new HashSet<>();
		for (JsonElement elem : bonesArray) {
			if (elem.isJsonObject()) {
				JsonObject bone = elem.getAsJsonObject();
				if (bone.has("name")) {
					boneNames.add(bone.get("name").getAsString());
				}
			}
		}

		if (boneNames.isEmpty()) {
			return new ValidationResult(false, "No named bones found in " + sourceName, Set.of());
		}

		return new ValidationResult(true, "Successfully validated " + boneNames.size() + " bones in " + sourceName, boneNames);
	}

	/**
	 * Validates that an expected animation exists inside an animation JSON stream.
	 */
	public static boolean validateAnimation(InputStream stream, String expectedAnim, String sourceName) {
		try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (!root.has("animations") || !root.get("animations").isJsonObject()) {
				Cozmetics.LOGGER.warn("Animation file '{}' missing 'animations' root object", sourceName);
				return false;
			}

			JsonObject anims = root.getAsJsonObject("animations");
			if (!anims.has(expectedAnim)) {
				Cozmetics.LOGGER.warn("Expected animation '{}' not found in '{}'. Available animations: {}",
						expectedAnim, sourceName, anims.keySet());
				return false;
			}

			return true;
		} catch (Exception e) {
			Cozmetics.LOGGER.error("Failed to parse animation JSON '{}': {}", sourceName, e.getMessage());
			return false;
		}
	}
}

