package io.github.itsarcn.cozmetics.client.render;

import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.GeckoLibResources;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.cache.model.GeoQuad;
import software.bernie.geckolib.cache.model.GeoVertex;
import software.bernie.geckolib.cache.model.cuboid.CuboidGeoBone;
import software.bernie.geckolib.cache.model.cuboid.GeoCube;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Computes and caches exact 3D visual bounding boxes and geometric centers
 * for any cosmetic model.
 */
public record CosmeticModelBounds(
		float centerX,
		float centerY,
		float centerZ,
		float spanX,
		float spanY,
		float spanZ,
		float maxSpan,
		boolean isDefault
) {
	private static final Map<Identifier, CosmeticModelBounds> CACHE = new ConcurrentHashMap<>();

	public static CosmeticModelBounds get(Cosmetic cosmetic) {
		if (cosmetic == null) {
			return defaultBounds();
		}
		CosmeticModelBounds cached = CACHE.get(cosmetic.getId());
		if (cached == null || cached.isDefault()) {
			CosmeticModelBounds computed = compute(cosmetic);
			CACHE.put(cosmetic.getId(), computed);
			return computed;
		}
		return cached;
	}

	public static void clearCache() {
		CACHE.clear();
	}

	private static CosmeticModelBounds compute(Cosmetic cosmetic) {
		try {
			if (GeckoLibResources.getBakedModels() != null) {
				BakedGeoModel bakedModel = GeckoLibResources.getBakedModels().getModel(cosmetic.getModelPath());
				if (bakedModel != null) {
					CosmeticModelBounds bounds = fromBakedModel(bakedModel);
					if (bounds != null) {
						return bounds;
					}
				}
			}
		} catch (Throwable ignored) {
			// Model might not be baked yet or running in unit test environment
		}

		// Fallback based on attachment slot
		return defaultForSlot(cosmetic);
	}

	public static CosmeticModelBounds fromBakedModel(BakedGeoModel model) {
		float minX = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;

		for (GeoBone bone : model.topLevelBones()) {
			float[] b = collectBoneBounds(bone);
			if (b != null) {
				minX = Math.min(minX, b[0]);
				maxX = Math.max(maxX, b[1]);
				minY = Math.min(minY, b[2]);
				maxY = Math.max(maxY, b[3]);
				minZ = Math.min(minZ, b[4]);
				maxZ = Math.max(maxZ, b[5]);
			}
		}

		if (Float.isInfinite(minX)) {
			return null;
		}

		float cX = (minX + maxX) / 2.0f;
		float cY = (minY + maxY) / 2.0f;
		float cZ = (minZ + maxZ) / 2.0f;
		float sX = Math.max(0.01f, maxX - minX);
		float sY = Math.max(0.01f, maxY - minY);
		float sZ = Math.max(0.01f, maxZ - minZ);
		float maxSpan = Math.max(sX, Math.max(sY, sZ));

		return new CosmeticModelBounds(cX, cY, cZ, sX, sY, sZ, maxSpan, false);
	}

	private static float[] collectBoneBounds(GeoBone bone) {
		float minX = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;
		boolean hasVertices = false;

		if (bone instanceof CuboidGeoBone cuboidBone) {
			for (GeoCube cube : cuboidBone.cubes) {
				if (cube.quads() == null) continue;
				for (GeoQuad quad : cube.quads()) {
					if (quad == null) continue;
					for (GeoVertex v : quad.vertices()) {
						hasVertices = true;
						minX = Math.min(minX, v.posX());
						maxX = Math.max(maxX, v.posX());
						minY = Math.min(minY, v.posY());
						maxY = Math.max(maxY, v.posY());
						minZ = Math.min(minZ, v.posZ());
						maxZ = Math.max(maxZ, v.posZ());
					}
				}
			}
		}

		if (bone.children() != null) {
			for (GeoBone child : bone.children()) {
				float[] cb = collectBoneBounds(child);
				if (cb != null) {
					hasVertices = true;
					minX = Math.min(minX, cb[0]);
					maxX = Math.max(maxX, cb[1]);
					minY = Math.min(minY, cb[2]);
					maxY = Math.max(maxY, cb[3]);
					minZ = Math.min(minZ, cb[4]);
					maxZ = Math.max(maxZ, cb[5]);
				}
			}
		}

		return hasVertices ? new float[]{minX, maxX, minY, maxY, minZ, maxZ} : null;
	}

	private static CosmeticModelBounds defaultForSlot(Cosmetic cosmetic) {
		float cY = switch (cosmetic.getAttachmentSlot()) {
			case HEAD -> 1.85f;
			case BODY -> (cosmetic.getCategory() == io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory.WINGS) ? 1.32f : 1.12f;
			case PET -> 1.72f;
			case ARMS -> 0.97f;
			case LEGS -> 0.19f;
		};
		float cX = (cosmetic.getAttachmentSlot() == io.github.itsarcn.cozmetics.cosmetic.AttachmentSlot.PET) ? 0.45f : 0.0f;
		float span = (cosmetic.getCategory() == io.github.itsarcn.cozmetics.cosmetic.CosmeticCategory.WINGS) ? 2.6f : 1.0f;
		return new CosmeticModelBounds(cX, cY, 0.0f, span, span, span, span, true);
	}

	private static CosmeticModelBounds defaultBounds() {
		return new CosmeticModelBounds(0.0f, 1.125f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, true);
	}
}
