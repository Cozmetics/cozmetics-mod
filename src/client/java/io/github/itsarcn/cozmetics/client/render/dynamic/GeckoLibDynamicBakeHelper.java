package io.github.itsarcn.cozmetics.client.render.dynamic;

import com.google.gson.JsonObject;
import io.github.itsarcn.cozmetics.Cozmetics;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.GeckoLibResources;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

/**
 * Utility helper to dynamically bake in-memory geometry and animations
 * into GeckoLib's runtime cache without restarting the client.
 */
public final class GeckoLibDynamicBakeHelper {

	private GeckoLibDynamicBakeHelper() {}

	public static void bakeModel(Identifier modelId, JsonObject geometryJson) {
		try {
			if (GeckoLibResources.getBakedModels() == null || GeckoLibResources.getBakedModels().cache() == null) {
				return;
			}
			Model model = GeckoLibResources.GSON.fromJson(geometryJson, Model.class);
			BakedGeoModel baked = BakedModelFactory.getForNamespace(modelId.getNamespace()).constructGeoModel(GeometryTree.fromModel(model));
			GeckoLibResources.getBakedModels().cache().put(modelId, baked);
			Cozmetics.LOGGER.info("Dynamically baked GeckoLib model for '{}'", modelId);
		} catch (Throwable t) {
			Cozmetics.LOGGER.warn("Could not bake dynamic GeckoLib model '{}': {}", modelId, t.getMessage());
		}
	}

	public static void bakeAnimations(Identifier animId, JsonObject animationJson) {
		try {
			if (GeckoLibResources.getBakedAnimations() == null || GeckoLibResources.getBakedAnimations().cache() == null) {
				return;
			}
			var animsObj = animationJson.has("animations") ? animationJson.getAsJsonObject("animations") : animationJson;
			BakedAnimations baked = GeckoLibResources.GSON.fromJson(animsObj, BakedAnimations.class);
			GeckoLibResources.getBakedAnimations().cache().put(animId, baked);
			Cozmetics.LOGGER.info("Dynamically baked GeckoLib animations for '{}'", animId);
		} catch (Throwable t) {
			Cozmetics.LOGGER.warn("Could not bake dynamic GeckoLib animations '{}': {}", animId, t.getMessage());
		}
	}
}
