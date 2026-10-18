package io.github.itsarcn.cozmetics.bbmodel;

import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * In-memory representation of a parsed Blockbench (.bbmodel) project.
 * Contains geometric elements (cubes), outliner/group hierarchy (bones),
 * texture definitions (including embedded base64 images), and animations.
 */
public class BbModel {
	private final String name;
	private final String formatVersion;
	private final String modelFormat;
	private final boolean boxUv;
	private final int resolutionWidth;
	private final int resolutionHeight;
	private final List<BbBone> rootBones = new ArrayList<>();
	private final Map<String, BbBone> bonesByName = new LinkedHashMap<>();
	private final Map<String, BbBone> bonesByUuid = new LinkedHashMap<>();
	private final List<BbTexture> textures = new ArrayList<>();
	private final List<BbAnimation> animations = new ArrayList<>();

	public BbModel(
			String name,
			String formatVersion,
			String modelFormat,
			boolean boxUv,
			int resolutionWidth,
			int resolutionHeight
	) {
		this.name = name;
		this.formatVersion = formatVersion;
		this.modelFormat = modelFormat;
		this.boxUv = boxUv;
		this.resolutionWidth = resolutionWidth > 0 ? resolutionWidth : 64;
		this.resolutionHeight = resolutionHeight > 0 ? resolutionHeight : 64;
	}

	public String getName() {
		return name;
	}

	public String getFormatVersion() {
		return formatVersion;
	}

	public String getModelFormat() {
		return modelFormat;
	}

	public boolean isBoxUv() {
		return boxUv;
	}

	public int getResolutionWidth() {
		return resolutionWidth;
	}

	public int getResolutionHeight() {
		return resolutionHeight;
	}

	public List<BbBone> getRootBones() {
		return Collections.unmodifiableList(rootBones);
	}

	public Collection<BbBone> getAllBones() {
		return Collections.unmodifiableCollection(bonesByName.values());
	}

	public @Nullable BbBone getBone(String name) {
		return bonesByName.get(name);
	}

	public @Nullable BbBone getBoneByUuid(String uuid) {
		return bonesByUuid.get(uuid);
	}

	public void addRootBone(BbBone bone) {
		this.rootBones.add(bone);
		registerBoneRecursive(bone);
	}

	private void registerBoneRecursive(BbBone bone) {
		this.bonesByName.put(bone.getName(), bone);
		if (bone.getUuid() != null) {
			this.bonesByUuid.put(bone.getUuid(), bone);
		}
		for (BbBone child : bone.getChildren()) {
			registerBoneRecursive(child);
		}
	}

	public List<BbTexture> getTextures() {
		return Collections.unmodifiableList(textures);
	}

	public void addTexture(BbTexture texture) {
		this.textures.add(texture);
	}

	public List<BbAnimation> getAnimations() {
		return Collections.unmodifiableList(animations);
	}

	public @Nullable BbAnimation getAnimation(String name) {
		for (BbAnimation anim : animations) {
			if (anim.getName().equals(name)) {
				return anim;
			}
		}
		return null;
	}

	public void addAnimation(BbAnimation animation) {
		this.animations.add(animation);
	}

	public static class BbBone {
		private final String name;
		private final String uuid;
		private @Nullable String parentName;
		private final float[] pivot;
		private final float[] rotation;
		private final List<BbCube> cubes = new ArrayList<>();
		private final List<BbBone> children = new ArrayList<>();

		public BbBone(String name, String uuid, float[] pivot, float[] rotation) {
			this.name = name;
			this.uuid = uuid;
			this.pivot = pivot != null ? pivot : new float[]{0, 0, 0};
			this.rotation = rotation != null ? rotation : new float[]{0, 0, 0};
		}

		public String getName() {
			return name;
		}

		public String getUuid() {
			return uuid;
		}

		public @Nullable String getParentName() {
			return parentName;
		}

		public void setParentName(@Nullable String parentName) {
			this.parentName = parentName;
		}

		public float[] getPivot() {
			return pivot;
		}

		public float[] getRotation() {
			return rotation;
		}

		public List<BbCube> getCubes() {
			return Collections.unmodifiableList(cubes);
		}

		public void addCube(BbCube cube) {
			this.cubes.add(cube);
		}

		public List<BbBone> getChildren() {
			return Collections.unmodifiableList(children);
		}

		public void addChild(BbBone child) {
			child.setParentName(this.name);
			this.children.add(child);
		}
	}

	public static class BbCube {
		private final String name;
		private final String uuid;
		private final float[] from;
		private final float[] to;
		private final float[] origin;
		private final float[] rotation;
		private final float inflate;
		private final boolean boxUv;
		private final float[] uvOffset;
		private final Map<String, BbFace> faces = new HashMap<>();

		public BbCube(
				String name,
				String uuid,
				float[] from,
				float[] to,
				float[] origin,
				float[] rotation,
				float inflate,
				boolean boxUv,
				float[] uvOffset
		) {
			this.name = name;
			this.uuid = uuid;
			this.from = from != null ? from : new float[]{0, 0, 0};
			this.to = to != null ? to : new float[]{0, 0, 0};
			this.origin = origin != null ? origin : new float[]{0, 0, 0};
			this.rotation = rotation != null ? rotation : new float[]{0, 0, 0};
			this.inflate = inflate;
			this.boxUv = boxUv;
			this.uvOffset = uvOffset != null ? uvOffset : new float[]{0, 0};
		}

		public String getName() {
			return name;
		}

		public String getUuid() {
			return uuid;
		}

		public float[] getFrom() {
			return from;
		}

		public float[] getTo() {
			return to;
		}

		public float[] getOrigin() {
			return origin;
		}

		public float[] getRotation() {
			return rotation;
		}

		public float getInflate() {
			return inflate;
		}

		public boolean isBoxUv() {
			return boxUv;
		}

		public float[] getUvOffset() {
			return uvOffset;
		}

		public Map<String, BbFace> getFaces() {
			return Collections.unmodifiableMap(faces);
		}

		public void setFace(String faceName, BbFace face) {
			this.faces.put(faceName, face);
		}
	}

	public record BbFace(float[] uv, int textureIndex) {}

	public static class BbTexture {
		private final String id;
		private final String name;
		private final @Nullable String path;
		private final @Nullable String sourceData;

		public BbTexture(String id, String name, @Nullable String path, @Nullable String sourceData) {
			this.id = id;
			this.name = name;
			this.path = path;
			this.sourceData = sourceData;
		}

		public String getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public @Nullable String getPath() {
			return path;
		}

		public @Nullable String getSourceData() {
			return sourceData;
		}

		public boolean hasEmbeddedData() {
			return sourceData != null && sourceData.startsWith("data:image/");
		}

		public byte @Nullable [] decodeEmbeddedBytes() {
			if (!hasEmbeddedData()) {
				return null;
			}
			try {
				int comma = sourceData.indexOf(',');
				if (comma > 0) {
					String base64 = sourceData.substring(comma + 1);
					return Base64.getDecoder().decode(base64);
				}
			} catch (Exception ignored) {
			}
			return null;
		}
	}

	public static class BbAnimation {
		private final String name;
		private final float length;
		private final String loop;
		private final Map<String, BbBoneAnimator> animators = new LinkedHashMap<>();

		public BbAnimation(String name, float length, String loop) {
			this.name = name;
			this.length = length;
			this.loop = loop != null ? loop : "loop";
		}

		public String getName() {
			return name;
		}

		public float getLength() {
			return length;
		}

		public String getLoop() {
			return loop;
		}

		public Map<String, BbBoneAnimator> getAnimators() {
			return Collections.unmodifiableMap(animators);
		}

		public void addAnimator(String target, BbBoneAnimator animator) {
			this.animators.put(target, animator);
		}
	}

	public static class BbBoneAnimator {
		private final String boneName;
		private final List<BbKeyframe> positionKeyframes = new ArrayList<>();
		private final List<BbKeyframe> rotationKeyframes = new ArrayList<>();
		private final List<BbKeyframe> scaleKeyframes = new ArrayList<>();

		public BbBoneAnimator(String boneName) {
			this.boneName = boneName;
		}

		public String getBoneName() {
			return boneName;
		}

		public List<BbKeyframe> getPositionKeyframes() {
			return Collections.unmodifiableList(positionKeyframes);
		}

		public List<BbKeyframe> getRotationKeyframes() {
			return Collections.unmodifiableList(rotationKeyframes);
		}

		public List<BbKeyframe> getScaleKeyframes() {
			return Collections.unmodifiableList(scaleKeyframes);
		}

		public void addPositionKeyframe(BbKeyframe kf) {
			this.positionKeyframes.add(kf);
		}

		public void addRotationKeyframe(BbKeyframe kf) {
			this.rotationKeyframes.add(kf);
		}

		public void addScaleKeyframe(BbKeyframe kf) {
			this.scaleKeyframes.add(kf);
		}
	}

	public record BbKeyframe(float time, float[] vector, String interpolation) {}
}

