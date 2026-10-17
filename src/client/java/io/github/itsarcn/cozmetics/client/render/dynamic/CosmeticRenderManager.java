package io.github.itsarcn.cozmetics.client.render.dynamic;

import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches and manages dynamic cosmetic renderers per cosmetic ID.
 * Prevents reallocating models or animatables each render pass.
 */
public class CosmeticRenderManager {
	private static final CosmeticRenderManager INSTANCE = new CosmeticRenderManager();
	private final Map<Identifier, DynamicCosmeticRenderer> rendererCache = new ConcurrentHashMap<>();

	private CosmeticRenderManager() {}

	public static CosmeticRenderManager getInstance() {
		return INSTANCE;
	}

	public DynamicCosmeticRenderer getRenderer(Cosmetic cosmetic) {
		return this.rendererCache.computeIfAbsent(cosmetic.getId(), id -> new DynamicCosmeticRenderer(cosmetic));
	}

	public void render(
			Cosmetic cosmetic,
			MatrixStack matrices,
			OrderedRenderCommandQueue queue,
			@Nullable CameraRenderState camera,
			int light,
			PlayerEntityRenderState playerState,
			PlayerEntityModel playerModel
	) {
		getRenderer(cosmetic).renderCosmetic(matrices, queue, camera, light, playerState, playerModel);
	}

	public void clearCache() {
		this.rendererCache.clear();
	}
}

