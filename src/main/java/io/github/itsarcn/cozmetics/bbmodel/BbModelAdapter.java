package io.github.itsarcn.cozmetics.bbmodel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.*;

/**
 * Adapts parsed {@link BbModel} project instances into GeckoLib/Minecraft Bedrock
 * runtime formats (Bedrock 1.12.0 geometry JSON and 1.8.0 animations JSON),
 * and extracts embedded base64 PNG textures.
 */
public final class BbModelAdapter {
	private BbModelAdapter() {}

	/**
	 * Adapts a BbModel into a Minecraft Bedrock 1.12.0 geometry JSON object.
	 */
	public static JsonObject toBedrockGeometry(BbModel model) {
		JsonObject root = new JsonObject();
		root.addProperty("format_version", "1.12.0");

		JsonArray geometries = new JsonArray();
		JsonObject geom = new JsonObject();

		JsonObject desc = new JsonObject();
		desc.addProperty("identifier", "geometry." + model.getName());
		desc.addProperty("texture_width", model.getResolutionWidth());
		desc.addProperty("texture_height", model.getResolutionHeight());
		desc.addProperty("visible_bounds_width", 3);
		desc.addProperty("visible_bounds_height", 3);
		JsonArray offset = new JsonArray();
		offset.add(0.0f);
		offset.add(1.5f);
		offset.add(0.0f);
		desc.add("visible_bounds_offset", offset);
		geom.add("description", desc);

		JsonArray bonesArray = new JsonArray();
		for (BbModel.BbBone rootBone : model.getRootBones()) {
			writeBoneRecursive(rootBone, bonesArray, model);
		}
		geom.add("bones", bonesArray);

		geometries.add(geom);
		root.add("minecraft:geometry", geometries);
		return root;
	}

	private static void writeBoneRecursive(BbModel.BbBone bone, JsonArray bonesArray, BbModel model) {
		JsonObject bObj = new JsonObject();
		bObj.addProperty("name", bone.getName());
		if (bone.getParentName() != null) {
			bObj.addProperty("parent", bone.getParentName());
		}

		JsonArray pivot = new JsonArray();
		pivot.add(bone.getPivot()[0]);
		pivot.add(bone.getPivot()[1]);
		pivot.add(bone.getPivot()[2]);
		bObj.add("pivot", pivot);

		if (bone.getRotation()[0] != 0.0f || bone.getRotation()[1] != 0.0f || bone.getRotation()[2] != 0.0f) {
			JsonArray rot = new JsonArray();
			rot.add(bone.getRotation()[0]);
			rot.add(bone.getRotation()[1]);
			rot.add(bone.getRotation()[2]);
			bObj.add("rotation", rot);
		}

		if (!bone.getCubes().isEmpty()) {
			JsonArray cubesArray = new JsonArray();
			for (BbModel.BbCube cube : bone.getCubes()) {
				JsonObject cObj = new JsonObject();

				JsonArray origin = new JsonArray();
				origin.add(cube.getFrom()[0]);
				origin.add(cube.getFrom()[1]);
				origin.add(cube.getFrom()[2]);
				cObj.add("origin", origin);

				JsonArray size = new JsonArray();
				float sx = Math.max(0.0f, cube.getTo()[0] - cube.getFrom()[0]);
				float sy = Math.max(0.0f, cube.getTo()[1] - cube.getFrom()[1]);
				float sz = Math.max(0.0f, cube.getTo()[2] - cube.getFrom()[2]);
				size.add(sx);
				size.add(sy);
				size.add(sz);
				cObj.add("size", size);

				if (cube.getRotation()[0] != 0.0f || cube.getRotation()[1] != 0.0f || cube.getRotation()[2] != 0.0f) {
					JsonArray cPivot = new JsonArray();
					cPivot.add(cube.getOrigin()[0]);
					cPivot.add(cube.getOrigin()[1]);
					cPivot.add(cube.getOrigin()[2]);
					cObj.add("pivot", cPivot);

					JsonArray cRot = new JsonArray();
					cRot.add(cube.getRotation()[0]);
					cRot.add(cube.getRotation()[1]);
					cRot.add(cube.getRotation()[2]);
					cObj.add("rotation", cRot);
				}

				if (cube.getInflate() != 0.0f) {
					cObj.addProperty("inflate", cube.getInflate());
				}

				// UV handling: box_uv or per-face
				if (cube.isBoxUv() && cube.getFaces().isEmpty()) {
					JsonArray uv = new JsonArray();
					uv.add(cube.getUvOffset()[0]);
					uv.add(cube.getUvOffset()[1]);
					cObj.add("uv", uv);
				} else if (!cube.getFaces().isEmpty()) {
					JsonObject facesObj = new JsonObject();
					for (Map.Entry<String, BbModel.BbFace> fEntry : cube.getFaces().entrySet()) {
						JsonObject faceData = new JsonObject();
						JsonArray uv = new JsonArray();
						float[] uvArr = fEntry.getValue().uv();
						if (uvArr.length >= 4) {
							uv.add(uvArr[0]);
							uv.add(uvArr[1]);
							JsonArray uvSize = new JsonArray();
							uvSize.add(uvArr[2] - uvArr[0]);
							uvSize.add(uvArr[3] - uvArr[1]);
							faceData.add("uv_size", uvSize);
						} else {
							uv.add(0.0f);
							uv.add(0.0f);
						}
						faceData.add("uv", uv);
						facesObj.add(fEntry.getKey().toLowerCase(), faceData);
					}
					cObj.add("uv", facesObj);
				} else {
					JsonArray uv = new JsonArray();
					uv.add(cube.getUvOffset()[0]);
					uv.add(cube.getUvOffset()[1]);
					cObj.add("uv", uv);
				}

				cubesArray.add(cObj);
			}
			bObj.add("cubes", cubesArray);
		}

		bonesArray.add(bObj);

		for (BbModel.BbBone child : bone.getChildren()) {
			writeBoneRecursive(child, bonesArray, model);
		}
	}

	/**
	 * Adapts BbModel animations into a Minecraft Bedrock 1.8.0 animation JSON object.
	 */
	public static JsonObject toBedrockAnimations(BbModel model) {
		JsonObject root = new JsonObject();
		root.addProperty("format_version", "1.8.0");

		JsonObject animsObj = new JsonObject();
		for (BbModel.BbAnimation anim : model.getAnimations()) {
			JsonObject animData = new JsonObject();
			animData.addProperty("animation_length", anim.getLength());

			if ("hold".equalsIgnoreCase(anim.getLoop())) {
				animData.addProperty("loop", "hold_on_last_frame");
			} else if ("once".equalsIgnoreCase(anim.getLoop())) {
				animData.addProperty("loop", false);
			} else {
				animData.addProperty("loop", true);
			}

			JsonObject bonesObj = new JsonObject();
			for (Map.Entry<String, BbModel.BbBoneAnimator> entry : anim.getAnimators().entrySet()) {
				BbModel.BbBoneAnimator animator = entry.getValue();
				JsonObject boneAnim = new JsonObject();

				// Rotation channel
				if (!animator.getRotationKeyframes().isEmpty()) {
					JsonObject rotTimeline = new JsonObject();
					for (BbModel.BbKeyframe kf : animator.getRotationKeyframes()) {
						JsonArray vec = new JsonArray();
						vec.add(kf.vector()[0]);
						vec.add(kf.vector()[1]);
						vec.add(kf.vector()[2]);
						rotTimeline.add(String.format(Locale.ROOT, "%.4f", kf.time()), vec);
					}
					boneAnim.add("rotation", rotTimeline);
				}

				// Position channel
				if (!animator.getPositionKeyframes().isEmpty()) {
					JsonObject posTimeline = new JsonObject();
					for (BbModel.BbKeyframe kf : animator.getPositionKeyframes()) {
						JsonArray vec = new JsonArray();
						vec.add(kf.vector()[0]);
						vec.add(kf.vector()[1]);
						vec.add(kf.vector()[2]);
						posTimeline.add(String.format(Locale.ROOT, "%.4f", kf.time()), vec);
					}
					boneAnim.add("position", posTimeline);
				}

				// Scale channel
				if (!animator.getScaleKeyframes().isEmpty()) {
					JsonObject scaleTimeline = new JsonObject();
					for (BbModel.BbKeyframe kf : animator.getScaleKeyframes()) {
						JsonArray vec = new JsonArray();
						vec.add(kf.vector()[0]);
						vec.add(kf.vector()[1]);
						vec.add(kf.vector()[2]);
						scaleTimeline.add(String.format(Locale.ROOT, "%.4f", kf.time()), vec);
					}
					boneAnim.add("scale", scaleTimeline);
				}

				if (boneAnim.size() > 0) {
					bonesObj.add(animator.getBoneName(), boneAnim);
				}
			}

			animData.add("bones", bonesObj);
			animsObj.add(anim.getName(), animData);
		}

		root.add("animations", animsObj);
		return root;
	}

	/**
	 * Extracts all embedded base64 PNG textures from the model.
	 *
	 * @return Map of texture name (or ID) to raw PNG byte array
	 */
	public static Map<String, byte[]> extractEmbeddedTextures(BbModel model) {
		Map<String, byte[]> textures = new LinkedHashMap<>();
		for (BbModel.BbTexture tex : model.getTextures()) {
			byte[] bytes = tex.decodeEmbeddedBytes();
			if (bytes != null && bytes.length > 0) {
				String key = tex.getName() != null ? tex.getName() : "texture_" + tex.getId() + ".png";
				textures.put(key, bytes);
			}
		}
		return textures;
	}
}

