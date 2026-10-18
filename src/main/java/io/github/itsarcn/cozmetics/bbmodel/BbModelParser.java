package io.github.itsarcn.cozmetics.bbmodel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.itsarcn.cozmetics.Cozmetics;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Robust JSON parser for Blockbench (.bbmodel) project files.
 * Supports Blockbench 3.x, 4.x, and 5.x formats, recursive outliners,
 * group definitions, box & per-face UV coordinates, embedded base64 textures,
 * and multi-channel skeletal animations.
 */
public final class BbModelParser {
	private BbModelParser() {}

	/**
	 * Parses a .bbmodel file from an InputStream.
	 */
	public static BbModel parse(InputStream in) throws Exception {
		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			return parse(root);
		}
	}

	/**
	 * Parses a .bbmodel file from a JSON string.
	 */
	public static BbModel parse(String jsonString) {
		JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();
		return parse(root);
	}

	/**
	 * Parses a .bbmodel project from a Gson JsonObject.
	 */
	public static BbModel parse(JsonObject root) {
		// 1. Metadata
		String formatVersion = "4.0";
		String modelFormat = "bedrock";
		boolean boxUv = false;
		if (root.has("meta") && root.get("meta").isJsonObject()) {
			JsonObject meta = root.getAsJsonObject("meta");
			if (meta.has("format_version")) formatVersion = meta.get("format_version").getAsString();
			if (meta.has("model_format")) modelFormat = meta.get("model_format").getAsString();
			if (meta.has("box_uv")) boxUv = meta.get("box_uv").getAsBoolean();
		}

		String name = root.has("name") ? root.get("name").getAsString() : "unnamed_model";

		int resW = 64;
		int resH = 64;
		if (root.has("resolution") && root.get("resolution").isJsonObject()) {
			JsonObject res = root.getAsJsonObject("resolution");
			if (res.has("width")) resW = res.get("width").getAsInt();
			if (res.has("height")) resH = res.get("height").getAsInt();
		}

		BbModel model = new BbModel(name, formatVersion, modelFormat, boxUv, resW, resH);

		// 2. Elements (Cubes)
		Map<String, BbModel.BbCube> cubesByUuid = new LinkedHashMap<>();
		if (root.has("elements") && root.get("elements").isJsonArray()) {
			JsonArray elements = root.getAsJsonArray("elements");
			for (JsonElement elem : elements) {
				if (elem.isJsonObject()) {
					BbModel.BbCube cube = parseCube(elem.getAsJsonObject(), boxUv);
					if (cube != null) {
						cubesByUuid.put(cube.getUuid(), cube);
					}
				}
			}
		}

		// 3. Groups & Outliner Hierarchy
		// In Blockbench 5.0, group properties can be in "groups" and hierarchy in "outliner"
		Map<String, JsonObject> groupDefsByUuid = new HashMap<>();
		if (root.has("groups") && root.get("groups").isJsonArray()) {
			for (JsonElement gElem : root.getAsJsonArray("groups")) {
				if (gElem.isJsonObject()) {
					JsonObject gObj = gElem.getAsJsonObject();
					if (gObj.has("uuid")) {
						groupDefsByUuid.put(gObj.get("uuid").getAsString(), gObj);
					}
				}
			}
		}

		if (root.has("outliner") && root.get("outliner").isJsonArray()) {
			JsonArray outliner = root.getAsJsonArray("outliner");
			for (JsonElement node : outliner) {
				parseOutlinerNode(node, null, model, cubesByUuid, groupDefsByUuid);
			}
		}

		// If outliner was empty or some cubes were unassigned to any bone, put them into a root "root" bone
		Set<String> assignedCubes = new HashSet<>();
		for (BbModel.BbBone bone : model.getAllBones()) {
			for (BbModel.BbCube cube : bone.getCubes()) {
				assignedCubes.add(cube.getUuid());
			}
		}

		List<BbModel.BbCube> unassigned = new ArrayList<>();
		for (Map.Entry<String, BbModel.BbCube> entry : cubesByUuid.entrySet()) {
			if (!assignedCubes.contains(entry.getKey())) {
				unassigned.add(entry.getValue());
			}
		}

		if (!unassigned.isEmpty()) {
			BbModel.BbBone rootBone = model.getBone("root");
			if (rootBone == null) {
				rootBone = new BbModel.BbBone("root", UUID.randomUUID().toString(), new float[]{0, 0, 0}, new float[]{0, 0, 0});
				model.addRootBone(rootBone);
			}
			for (BbModel.BbCube cube : unassigned) {
				rootBone.addCube(cube);
			}
		}

		// 4. Textures
		if (root.has("textures") && root.get("textures").isJsonArray()) {
			for (JsonElement tElem : root.getAsJsonArray("textures")) {
				if (tElem.isJsonObject()) {
					JsonObject tObj = tElem.getAsJsonObject();
					String id = tObj.has("id") ? tObj.get("id").getAsString() : "0";
					String texName = tObj.has("name") ? tObj.get("name").getAsString() : "texture_" + id + ".png";
					String path = tObj.has("path") ? tObj.get("path").getAsString() : null;
					String source = tObj.has("source") ? tObj.get("source").getAsString() : null;
					model.addTexture(new BbModel.BbTexture(id, texName, path, source));
				}
			}
		}

		// 5. Animations
		if (root.has("animations") && root.get("animations").isJsonArray()) {
			for (JsonElement aElem : root.getAsJsonArray("animations")) {
				if (aElem.isJsonObject()) {
					BbModel.BbAnimation anim = parseAnimation(aElem.getAsJsonObject(), model);
					if (anim != null) {
						model.addAnimation(anim);
					}
				}
			}
		}

		return model;
	}

	private static BbModel.@Nullable BbCube parseCube(JsonObject obj, boolean defaultBoxUv) {
		String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() : UUID.randomUUID().toString();
		String name = obj.has("name") ? obj.get("name").getAsString() : "cube";

		float[] from = parseFloatArray(obj.get("from"), 3, 0.0f);
		float[] to = parseFloatArray(obj.get("to"), 3, 0.0f);
		float[] origin = parseFloatArray(obj.get("origin"), 3, 0.0f);
		float[] rotation = parseFloatArray(obj.get("rotation"), 3, 0.0f);
		float inflate = obj.has("inflate") ? obj.get("inflate").getAsFloat() : 0.0f;

		boolean boxUv = obj.has("box_uv") ? obj.get("box_uv").getAsBoolean() : defaultBoxUv;
		float[] uvOffset = parseFloatArray(obj.get("uv_offset"), 2, 0.0f);

		BbModel.BbCube cube = new BbModel.BbCube(name, uuid, from, to, origin, rotation, inflate, boxUv, uvOffset);

		if (obj.has("faces") && obj.get("faces").isJsonObject()) {
			JsonObject facesObj = obj.getAsJsonObject("faces");
			for (String faceName : facesObj.keySet()) {
				JsonElement fElem = facesObj.get(faceName);
				if (fElem.isJsonObject()) {
					JsonObject fObj = fElem.getAsJsonObject();
					float[] uv = parseFloatArray(fObj.get("uv"), 4, 0.0f);
					int tex = fObj.has("texture") && !fObj.get("texture").isJsonNull() ? fObj.get("texture").getAsInt() : 0;
					cube.setFace(faceName, new BbModel.BbFace(uv, tex));
				}
			}
		}

		return cube;
	}

	private static void parseOutlinerNode(
			JsonElement node,
			BbModel.@Nullable BbBone parentBone,
			BbModel model,
			Map<String, BbModel.BbCube> cubesByUuid,
			Map<String, JsonObject> groupDefsByUuid
	) {
		if (node.isJsonPrimitive() && node.getAsJsonPrimitive().isString()) {
			// A string UUID referencing a cube element
			String cubeUuid = node.getAsString();
			BbModel.BbCube cube = cubesByUuid.get(cubeUuid);
			if (cube != null && parentBone != null) {
				parentBone.addCube(cube);
			}
			return;
		}

		if (node.isJsonObject()) {
			JsonObject obj = node.getAsJsonObject();
			String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() : UUID.randomUUID().toString();

			// Merge with group definition if separated (Blockbench 5.0)
			JsonObject groupDef = groupDefsByUuid.get(uuid);
			if (groupDef != null) {
				for (String key : groupDef.keySet()) {
					if (!obj.has(key)) {
						obj.add(key, groupDef.get(key));
					}
				}
			}

			// Check if this object represents a cube directly (in some rare exports)
			if (obj.has("from") && obj.has("to")) {
				BbModel.BbCube cube = parseCube(obj, model.isBoxUv());
				if (cube != null && parentBone != null) {
					parentBone.addCube(cube);
				}
				return;
			}

			String name = obj.has("name") ? obj.get("name").getAsString() : "bone_" + uuid.substring(0, 8);
			float[] origin = parseFloatArray(obj.get("origin"), 3, 0.0f);
			float[] rotation = parseFloatArray(obj.get("rotation"), 3, 0.0f);

			BbModel.BbBone bone = new BbModel.BbBone(name, uuid, origin, rotation);

			if (obj.has("children") && obj.get("children").isJsonArray()) {
				for (JsonElement child : obj.getAsJsonArray("children")) {
					parseOutlinerNode(child, bone, model, cubesByUuid, groupDefsByUuid);
				}
			}

			if (parentBone != null) {
				parentBone.addChild(bone);
			} else {
				model.addRootBone(bone);
			}
		}
	}

	private static BbModel.@Nullable BbAnimation parseAnimation(JsonObject obj, BbModel model) {
		String animName = obj.has("name") ? obj.get("name").getAsString() : "animation";
		float length = obj.has("length") ? obj.get("length").getAsFloat() : 0.0f;
		String loop = obj.has("loop") ? obj.get("loop").getAsString() : "loop";

		BbModel.BbAnimation animation = new BbModel.BbAnimation(animName, length, loop);

		if (obj.has("animators") && obj.get("animators").isJsonObject()) {
			JsonObject animatorsObj = obj.getAsJsonObject("animators");
			for (Map.Entry<String, JsonElement> entry : animatorsObj.entrySet()) {
				String targetId = entry.getKey();
				if (!entry.getValue().isJsonObject()) continue;
				JsonObject animObj = entry.getValue().getAsJsonObject();

				// Resolve target bone name
				String boneName = targetId;
				if (animObj.has("name")) {
					boneName = animObj.get("name").getAsString();
				} else {
					BbModel.BbBone foundBone = model.getBoneByUuid(targetId);
					if (foundBone != null) {
						boneName = foundBone.getName();
					}
				}

				BbModel.BbBoneAnimator animator = new BbModel.BbBoneAnimator(boneName);

				// Blockbench keyframes can be inside "keyframes" array or separate channel arrays
				if (animObj.has("keyframes") && animObj.get("keyframes").isJsonArray()) {
					for (JsonElement kElem : animObj.getAsJsonArray("keyframes")) {
						if (!kElem.isJsonObject()) continue;
						JsonObject kObj = kElem.getAsJsonObject();
						String channel = kObj.has("channel") ? kObj.get("channel").getAsString().toLowerCase() : "rotation";
						float time = kObj.has("time") ? kObj.get("time").getAsFloat() : 0.0f;
						String interp = kObj.has("interpolation") ? kObj.get("interpolation").getAsString() : "linear";

						float[] vec = extractKeyframeVector(kObj);
						BbModel.BbKeyframe kf = new BbModel.BbKeyframe(time, vec, interp);

						switch (channel) {
							case "rotation" -> animator.addRotationKeyframe(kf);
							case "position" -> animator.addPositionKeyframe(kf);
							case "scale" -> animator.addScaleKeyframe(kf);
						}
					}
				}

				// Legacy/alternate format where channels are arrays directly on animObj
				parseChannelArray(animObj.getAsJsonArray("rotation"), animator::addRotationKeyframe);
				parseChannelArray(animObj.getAsJsonArray("position"), animator::addPositionKeyframe);
				parseChannelArray(animObj.getAsJsonArray("scale"), animator::addScaleKeyframe);

				animation.addAnimator(boneName, animator);
			}
		}

		return animation;
	}

	private static void parseChannelArray(@Nullable JsonArray array, java.util.function.Consumer<BbModel.BbKeyframe> consumer) {
		if (array == null) return;
		for (JsonElement elem : array) {
			if (!elem.isJsonObject()) continue;
			JsonObject kObj = elem.getAsJsonObject();
			float time = kObj.has("time") ? kObj.get("time").getAsFloat() : 0.0f;
			String interp = kObj.has("interpolation") ? kObj.get("interpolation").getAsString() : "linear";
			float[] vec = extractKeyframeVector(kObj);
			consumer.accept(new BbModel.BbKeyframe(time, vec, interp));
		}
	}

	private static float[] extractKeyframeVector(JsonObject kObj) {
		if (kObj.has("data_points") && kObj.get("data_points").isJsonArray()) {
			JsonArray points = kObj.getAsJsonArray("data_points");
			if (!points.isEmpty()) {
				JsonElement first = points.get(0);
				if (first.isJsonObject()) {
					JsonObject p = first.getAsJsonObject();
					float x = p.has("x") ? p.get("x").getAsFloat() : 0.0f;
					float y = p.has("y") ? p.get("y").getAsFloat() : 0.0f;
					float z = p.has("z") ? p.get("z").getAsFloat() : 0.0f;
					return new float[]{x, y, z};
				} else if (first.isJsonPrimitive()) {
					return parseFloatArray(points, 3, 0.0f);
				}
			}
		}
		if (kObj.has("vector")) {
			return parseFloatArray(kObj.get("vector"), 3, 0.0f);
		}
		return new float[]{0.0f, 0.0f, 0.0f};
	}

	private static float[] parseFloatArray(@Nullable JsonElement elem, int size, float defaultValue) {
		float[] result = new float[size];
		Arrays.fill(result, defaultValue);
		if (elem != null && elem.isJsonArray()) {
			JsonArray arr = elem.getAsJsonArray();
			for (int i = 0; i < Math.min(size, arr.size()); i++) {
				try {
					result[i] = arr.get(i).getAsFloat();
				} catch (Exception ignored) {}
			}
		}
		return result;
	}
}
