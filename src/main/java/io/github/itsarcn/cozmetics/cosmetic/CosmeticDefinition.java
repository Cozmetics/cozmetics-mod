package io.github.itsarcn.cozmetics.cosmetic;

import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Standard implementation of {@link Cosmetic} representing a registered cosmetic definition.
 */
public record CosmeticDefinition(
		Identifier id,
		CosmeticCategory category,
		String name,
		Identifier modelPath,
		Identifier texturePath,
		int textureFrameCount,
		int textureFrameDurationMs,
		@Nullable Identifier animationPath,
		@Nullable String idleAnimationName,
		AttachmentSlot attachmentSlot,
		AttachmentTransform transform
) implements Cosmetic {

	@Override
	public Identifier getId() {
		return id;
	}

	@Override
	public CosmeticCategory getCategory() {
		return category;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Identifier getModelPath() {
		return modelPath;
	}

	@Override
	public Identifier getTexturePath() {
		return texturePath;
	}

	@Override
	public int getTextureFrameCount() {
		return textureFrameCount;
	}

	@Override
	public int getTextureFrameDurationMs() {
		return textureFrameDurationMs;
	}

	@Override
	public @Nullable Identifier getAnimationPath() {
		return animationPath;
	}

	@Override
	public @Nullable String getIdleAnimationName() {
		return idleAnimationName;
	}

	@Override
	public AttachmentSlot getAttachmentSlot() {
		return attachmentSlot;
	}

	@Override
	public AttachmentTransform getTransform() {
		return transform;
	}

	public static Builder builder(Identifier id, CosmeticCategory category, String name) {
		return new Builder(id, category, name);
	}

	public static class Builder {
		private final Identifier id;
		private final CosmeticCategory category;
		private final String name;
		private Identifier modelPath;
		private Identifier texturePath;
		private int textureFrameCount = 1;
		private int textureFrameDurationMs = 100;
		private Identifier animationPath;
		private String idleAnimationName;
		private AttachmentSlot attachmentSlot;
		private AttachmentTransform transform = AttachmentTransform.DEFAULT;

		public Builder(Identifier id, CosmeticCategory category, String name) {
			this.id = id;
			this.category = category;
			this.name = name;
		}

		public Builder model(Identifier modelPath) {
			this.modelPath = modelPath;
			return this;
		}

		public Builder texture(Identifier texturePath) {
			this.texturePath = texturePath;
			return this;
		}

		public Builder textureFrames(int frameCount, int durationMs) {
			this.textureFrameCount = frameCount;
			this.textureFrameDurationMs = durationMs;
			return this;
		}

		public Builder animation(@Nullable Identifier animationPath, @Nullable String idleAnimationName) {
			this.animationPath = animationPath;
			this.idleAnimationName = idleAnimationName;
			return this;
		}

		public Builder slot(AttachmentSlot slot) {
			this.attachmentSlot = slot;
			return this;
		}

		public Builder transform(AttachmentTransform transform) {
			this.transform = transform;
			return this;
		}

		public CosmeticDefinition build() {
			AttachmentSlot resolvedSlot = this.attachmentSlot != null ? this.attachmentSlot : switch (category) {
				case PET -> AttachmentSlot.PET;
				case WINGS, BACK, AURA -> AttachmentSlot.BODY;
				case HEADWEAR, FACE -> AttachmentSlot.HEAD;
				case FISTS -> AttachmentSlot.ARMS;
				case BOOTS -> AttachmentSlot.LEGS;
				case CAPE -> AttachmentSlot.BODY;
			};
			return new CosmeticDefinition(
					id, category, name, modelPath, texturePath,
					textureFrameCount, textureFrameDurationMs,
					animationPath, idleAnimationName, resolvedSlot, transform
			);
		}
	}

	/**
	 * Deserializes a CosmeticDefinition from JSON schema.
	 */
	public static CosmeticDefinition fromJson(com.google.gson.JsonObject json) {
		if (!json.has("id") || !json.has("name") || !json.has("category")) {
			throw new IllegalArgumentException("Cosmetic JSON missing required fields: id, name, or category");
		}

		Identifier id = Identifier.of(json.get("id").getAsString());
		String name = json.get("name").getAsString();
		String catStr = json.get("category").getAsString();
		CosmeticCategory category = CosmeticCategory.valueOf(catStr);

		if (!category.isEnabled()) {
			throw new IllegalArgumentException("Category is disabled: " + category);
		}

		Builder builder = builder(id, category, name);

		if (json.has("model")) {
			builder.model(Identifier.of(json.get("model").getAsString()));
		}
		if (json.has("texture")) {
			builder.texture(Identifier.of(json.get("texture").getAsString()));
		}
		if (json.has("texture_frames")) {
			int frames = json.get("texture_frames").getAsInt();
			int dur = json.has("texture_frame_duration_ms") ? json.get("texture_frame_duration_ms").getAsInt() : 100;
			builder.textureFrames(frames, dur);
		}
		if (json.has("animation")) {
			Identifier animPath = Identifier.of(json.get("animation").getAsString());
			String idleName = json.has("idle_animation") ? json.get("idle_animation").getAsString() : null;
			builder.animation(animPath, idleName);
		}
		if (json.has("slot")) {
			builder.slot(AttachmentSlot.valueOf(json.get("slot").getAsString()));
		}
		if (json.has("transform") && json.get("transform").isJsonObject()) {
			com.google.gson.JsonObject tObj = json.getAsJsonObject("transform");
			float translateY = tObj.has("translate_y") ? tObj.get("translate_y").getAsFloat() : 0.0f;
			float scale = tObj.has("scale") ? tObj.get("scale").getAsFloat() : 1.0f;
			boolean staticLight = tObj.has("static_lighting") && tObj.get("static_lighting").getAsBoolean();
			builder.transform(new AttachmentTransform(translateY, scale, staticLight));
		}

		return builder.build();
	}

	/**
	 * Serializes this definition to a JSON representation.
	 */
	public com.google.gson.JsonObject toJson() {
		com.google.gson.JsonObject json = new com.google.gson.JsonObject();
		json.addProperty("id", this.id.toString());
		json.addProperty("name", this.name);
		json.addProperty("category", this.category.name());
		if (this.modelPath != null) json.addProperty("model", this.modelPath.toString());
		if (this.texturePath != null) json.addProperty("texture", this.texturePath.toString());
		if (this.textureFrameCount > 1) {
			json.addProperty("texture_frames", this.textureFrameCount);
			json.addProperty("texture_frame_duration_ms", this.textureFrameDurationMs);
		}
		if (this.animationPath != null) {
			json.addProperty("animation", this.animationPath.toString());
			if (this.idleAnimationName != null) json.addProperty("idle_animation", this.idleAnimationName);
		}
		if (this.attachmentSlot != null) {
			json.addProperty("slot", this.attachmentSlot.name());
		}
		if (this.transform != null && this.transform != AttachmentTransform.DEFAULT) {
			com.google.gson.JsonObject tObj = new com.google.gson.JsonObject();
			tObj.addProperty("translate_y", this.transform.translateY());
			tObj.addProperty("scale", this.transform.scale());
			tObj.addProperty("static_lighting", this.transform.staticLighting());
			json.add("transform", tObj);
		}
		return json;
	}
}
