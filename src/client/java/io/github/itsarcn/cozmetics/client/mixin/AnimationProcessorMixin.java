package io.github.itsarcn.cozmetics.client.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.state.ControllerState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.loading.math.MolangQueries;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.util.ClientUtil;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Mixin to GeckoLib's AnimationProcessor allowing animation controllers to evaluate
 * on the Title Screen / Main Menu when no live Minecraft level or player entity is active.
 */
@Mixin(AnimationProcessor.class)
public class AnimationProcessorMixin {
	@Inject(method = "extractControllerStates", at = @At("HEAD"), cancellable = true)
	private static <T extends GeoAnimatable> void cozmetics$extractControllerStatesOnTitleScreen(
			T animatable, GeoRenderState renderState, GeoModel<T> geoModel, CallbackInfo ci
	) {
		// When on the title screen / main menu, level and player are null.
		// Run animation controllers with fallback null level/player so idle animations play smoothly.
		if (ClientUtil.getLevel() == null || ClientUtil.getClientPlayer() == null) {
			AnimatableManager<T> manager = Objects.requireNonNull(renderState.getGeckolibData(DataTickets.ANIMATABLE_MANAGER));
			Collection<AnimationController<T>> controllers = manager.getAnimationControllers().values();
			double tick = renderState.getAnimatableAge();
			List<ControllerState> controllerStates = new ObjectArrayList<>(controllers.size());

			manager.markRenderedAt(tick);

			if (!controllers.isEmpty()) {
				float partialTick = renderState.getPartialTick();
				double renderTime = manager.getFirstRenderTick() - tick;

				for (AnimationController<T> controller : controllers) {
					MolangQueries.Actor<T> actor = new MolangQueries.Actor<>(
							animatable, renderState, controller, renderTime, partialTick, null, null, Vec3d.ZERO
					);
					ControllerState controllerState = controller.extractControllerState(animatable, renderState, manager, actor, geoModel);

					if (controllerState != null) {
						controllerStates.add(controllerState);
					}
				}
			}

			renderState.addGeckolibData(DataTickets.ANIMATION_CONTROLLER_STATES, controllerStates.toArray(new ControllerState[0]));
			ci.cancel();
		}
	}
}

