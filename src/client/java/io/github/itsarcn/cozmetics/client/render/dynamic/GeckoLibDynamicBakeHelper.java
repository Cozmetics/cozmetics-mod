package io.github.itsarcn.cozmetics.client.render.dynamic;

import com.google.gson.JsonObject;
import io.github.itsarcn.cozmetics.Cozmetics;
import net.minecraft.util.Identifier;
//? if =1.21.11 {
import software.bernie.geckolib.cache.GeckoLibResources;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
//?} else if <=1.21.10 && >=1.21.5 {
/*import software.bernie.geckolib.cache.GeckoLibResources;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;*/
//?} else if <=1.21.4 {
/*import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;*/
//?}

/**
 * Utility helper to dynamically bake in-memory geometry and animations
 * into GeckoLib's runtime cache without restarting the client.
 */
public final class GeckoLibDynamicBakeHelper {

	private GeckoLibDynamicBakeHelper() {}

	public static void bakeModel(Identifier modelId, JsonObject geometryJson) {
		try {
			//? if =1.21.11 {
			if (GeckoLibResources.getBakedModels() == null || GeckoLibResources.getBakedModels().cache() == null) {
				return;
			}
			Model model = GeckoLibResources.GSON.fromJson(geometryJson, Model.class);
			BakedGeoModel baked = BakedModelFactory.getForNamespace(modelId.getNamespace()).constructGeoModel(GeometryTree.fromModel(model));
			GeckoLibResources.getBakedModels().cache().put(modelId, baked);
			//?} else if <=1.21.10 && >=1.21.5 {
			/*if (GeckoLibResources.getBakedModels() == null) {
				return;
			}
			Model model = KeyFramesAdapter.GEO_GSON.fromJson(geometryJson, Model.class);
			BakedGeoModel baked = BakedModelFactory.getForNamespace(modelId.getNamespace()).constructGeoModel(GeometryTree.fromModel(model));
			GeckoLibResources.getBakedModels().put(modelId, baked);
			*///?} else if <=1.21.4 {
			/*if (GeckoLibCache.getBakedModels() == null) {
				return;
			}
			Model model = KeyFramesAdapter.GEO_GSON.fromJson(geometryJson, Model.class);
			BakedGeoModel baked = BakedModelFactory.getForNamespace(modelId.getNamespace()).constructGeoModel(GeometryTree.fromModel(model));
			GeckoLibCache.getBakedModels().put(modelId, baked);
			*///?}
			Cozmetics.LOGGER.info("Dynamically baked GeckoLib model for '{}'", modelId);
		} catch (Throwable t) {
			Cozmetics.LOGGER.warn("Could not bake dynamic GeckoLib model '{}': {}", modelId, t.getMessage());
		}
	}

	public static void bakeAnimations(Identifier animId, JsonObject animationJson) {
		try {
			var animsObj = animationJson.has("animations") ? animationJson.getAsJsonObject("animations") : animationJson;
			//? if =1.21.11 {
			if (GeckoLibResources.getBakedAnimations() == null || GeckoLibResources.getBakedAnimations().cache() == null) {
				return;
			}
			BakedAnimations baked = GeckoLibResources.GSON.fromJson(animsObj, BakedAnimations.class);
			GeckoLibResources.getBakedAnimations().cache().put(animId, baked);
			//?} else if <=1.21.10 && >=1.21.5 {
			/*if (GeckoLibResources.getBakedAnimations() == null) {
				return;
			}
			BakedAnimations baked = KeyFramesAdapter.GEO_GSON.fromJson(animsObj, BakedAnimations.class);
			GeckoLibResources.getBakedAnimations().put(animId, baked);
			*///?} else if <=1.21.4 {
			/*if (GeckoLibCache.getBakedAnimations() == null) {
				return;
			}
			BakedAnimations baked = KeyFramesAdapter.GEO_GSON.fromJson(animsObj, BakedAnimations.class);
			GeckoLibCache.getBakedAnimations().put(animId, baked);
			GeckoLibCache.getBakedAnimations().put(Identifier.of(animId.getNamespace(), "animations/" + animId.getPath() + ".animation.json"), baked);
			*///?}
			Cozmetics.LOGGER.info("Dynamically baked GeckoLib animations for '{}'", animId);
		} catch (Throwable t) {
			Cozmetics.LOGGER.warn("Could not bake dynamic GeckoLib animations '{}': {}", animId, t.getMessage());
		}
	}
}
