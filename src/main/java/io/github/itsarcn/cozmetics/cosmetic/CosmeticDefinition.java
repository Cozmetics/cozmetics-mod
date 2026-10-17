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
}
