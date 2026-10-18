package io.github.itsarcn.cozmetics.bbmodel;

import com.google.gson.JsonObject;
import io.github.itsarcn.cozmetics.Cozmetics;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime manager providing direct, cached loading and adaptation of Blockbench (.bbmodel) models.
 * Feeds the dynamic rendering, texture decoding, and GeckoLib animation pipeline directly in memory.
 */
public final class BbModelManager {
	private static final BbModelManager INSTANCE = new BbModelManager();
	private final Map<Identifier, BbModel> models = new ConcurrentHashMap<>();
	private final Map<Identifier, JsonObject> geometryCache = new ConcurrentHashMap<>();
	private final Map<Identifier, JsonObject> animationCache = new ConcurrentHashMap<>();

	private BbModelManager() {}

	public static BbModelManager getInstance() {
		return INSTANCE;
	}

	/**
	 * Registers and adapts a parsed BbModel into the runtime manager.
	 */
	public BbModel register(Identifier id, BbModel model) {
		this.models.put(id, model);
		this.geometryCache.put(id, BbModelAdapter.toBedrockGeometry(model));
		this.animationCache.put(id, BbModelAdapter.toBedrockAnimations(model));
		return model;
	}

	/**
	 * Loads a .bbmodel from an InputStream and registers it under the specified Identifier.
	 */
	public Optional<BbModel> load(Identifier id, InputStream in) {
		try {
			BbModel model = BbModelParser.parse(in);
			register(id, model);
			Cozmetics.LOGGER.info("Successfully loaded and adapted .bbmodel '{}' ({} bones, {} animations)",
					id, model.getAllBones().size(), model.getAnimations().size());
			return Optional.of(model);
		} catch (Exception e) {
			Cozmetics.LOGGER.error("Failed to load .bbmodel from stream for '{}'", id, e);
			return Optional.empty();
		}
	}

	public Optional<BbModel> get(Identifier id) {
		return Optional.ofNullable(this.models.get(id));
	}

	public @Nullable JsonObject getGeometryJson(Identifier id) {
		return this.geometryCache.get(id);
	}

	public @Nullable JsonObject getAnimationJson(Identifier id) {
		return this.animationCache.get(id);
	}

	public boolean hasModel(Identifier id) {
		return this.models.containsKey(id);
	}

	public void clear() {
		this.models.clear();
		this.geometryCache.clear();
		this.animationCache.clear();
	}
}

