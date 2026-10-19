package io.github.itsarcn.cozmetics.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.itsarcn.cozmetics.cosmetic.AttachmentSlot;
import io.github.itsarcn.cozmetics.cosmetic.AttachmentTransform;
import io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Manifest definition contained inside a cosmetic ZIP package (manifest.json).
 *
 * Defines the package ID, version, paths to model/texture/animation files within the ZIP,
 * attachment slot, and rendering transforms.
 *
 * Notice: Presentation metadata like name, category, and thumbnail URL are decoupled
 * and managed by the remote catalogue metadata, so changing cosmetic display names
 * or thumbnails does not require re-downloading or modifying this package.
 */
public record CosmeticPackageManifest(
		Identifier id,
		String version,
		String modelPath,
		String texturePath,
		@Nullable String animationPath,
		@Nullable String idleAnimationName,
		AttachmentSlot slot,
		AttachmentTransform transform
) {

	public static CosmeticPackageManifest parse(String jsonString) {
		JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
		return parseJson(json);
	}

	public static CosmeticPackageManifest parse(InputStream inputStream) {
		JsonObject json = JsonParser.parseReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).getAsJsonObject();
		return parseJson(json);
	}

	public static CosmeticPackageManifest parseJson(JsonObject json) {
		String idStr = json.has("id") ? json.get("id").getAsString() : "";
		if (idStr.isBlank()) {
			throw new IllegalArgumentException("Manifest missing required 'id' field");
		}
		Identifier id = idStr.contains(":") ? Identifier.of(idStr) : Identifier.of("cozmetics", idStr);

		String version = json.has("version") ? json.get("version").getAsString() : "1.0.0";

		String modelPath = json.has("model") ? json.get("model").getAsString() : "";
		if (modelPath.isBlank()) {
			throw new IllegalArgumentException("Manifest missing required 'model' field");
		}

		String texturePath = json.has("texture") ? json.get("texture").getAsString() : "";
		if (texturePath.isBlank()) {
			throw new IllegalArgumentException("Manifest missing required 'texture' field");
		}

		String animationPath = json.has("animation") && !json.get("animation").isJsonNull()
				? json.get("animation").getAsString() : null;

		String idleAnim = json.has("idle_animation") && !json.get("idle_animation").isJsonNull()
				? json.get("idle_animation").getAsString() : null;

		AttachmentSlot slot = AttachmentSlot.BODY;
		if (json.has("slot") && !json.get("slot").isJsonNull()) {
			try {
				slot = AttachmentSlot.valueOf(json.get("slot").getAsString().toUpperCase());
			} catch (IllegalArgumentException ignored) {
			}
		}

		AttachmentTransform transform = AttachmentTransform.DEFAULT;
		if (json.has("transform") && json.get("transform").isJsonObject()) {
			JsonObject t = json.getAsJsonObject("transform");
			float translateY = t.has("translate_y") ? t.get("translate_y").getAsFloat() : 0.0f;
			float scale = t.has("scale") ? t.get("scale").getAsFloat() : 1.0f;
			boolean staticLighting = t.has("static_lighting") && t.get("static_lighting").getAsBoolean();
			transform = new AttachmentTransform(translateY, scale, staticLighting);
		}

		return new CosmeticPackageManifest(
				id,
				version,
				modelPath,
				texturePath,
				animationPath,
				idleAnim,
				slot,
				transform
		);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("id", id.toString());
		json.addProperty("version", version);
		json.addProperty("model", modelPath);
		json.addProperty("texture", texturePath);
		if (animationPath != null) {
			json.addProperty("animation", animationPath);
		}
		if (idleAnimationName != null) {
			json.addProperty("idle_animation", idleAnimationName);
		}
		json.addProperty("slot", slot.name());

		JsonObject t = new JsonObject();
		t.addProperty("translate_y", transform.translateY());
		t.addProperty("scale", transform.scale());
		t.addProperty("static_lighting", transform.staticLighting());
		json.add("transform", t);

		return json;
	}
}
