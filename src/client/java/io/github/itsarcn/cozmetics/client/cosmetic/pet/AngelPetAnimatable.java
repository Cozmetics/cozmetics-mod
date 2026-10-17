package io.github.itsarcn.cozmetics.client.cosmetic.pet;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * GeoAnimatable implementation for the Angel Pet.
 * Plays the looping idle animation provided in the asset pack.
 */
public class AngelPetAnimatable implements GeoAnimatable {
	private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.angelPet.idle");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<AngelPetAnimatable>("idle", 0, state -> state.setAndContinue(IDLE_ANIM)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
