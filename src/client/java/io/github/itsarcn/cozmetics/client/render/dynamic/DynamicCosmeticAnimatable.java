package io.github.itsarcn.cozmetics.client.render.dynamic;

import io.github.itsarcn.cozmetics.cosmetic.Cosmetic;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
//? if <=1.21.10 && >=1.21.5 {
/*import software.bernie.geckolib.animatable.processing.AnimationController;*/
//?} else {
import software.bernie.geckolib.animation.AnimationController;
//?}
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Dynamic GeoAnimatable implementation for any Cosmetic.
 * Automatically configures looping idle animations for animated cosmetics.
 */
public class DynamicCosmeticAnimatable implements GeoAnimatable {
	private final Cosmetic cosmetic;
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public DynamicCosmeticAnimatable(Cosmetic cosmetic) {
		this.cosmetic = cosmetic;
	}

	public Cosmetic getCosmetic() {
		return this.cosmetic;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		if (this.cosmetic.isAnimated() && this.cosmetic.getIdleAnimationName() != null) {
			RawAnimation idleAnim = RawAnimation.begin().thenLoop(this.cosmetic.getIdleAnimationName());
			controllers.add(new AnimationController<DynamicCosmeticAnimatable>("main", 0, state -> state.setAndContinue(idleAnim)));
		}
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	//? if <1.21.11 {
	/*@Override
	public double getTick(@org.jspecify.annotations.Nullable Object object) {
		return (System.currentTimeMillis() % 100000000L) / 50.0;
	}*///?}
}

