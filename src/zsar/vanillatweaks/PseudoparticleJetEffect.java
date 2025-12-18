package zsar.vanillatweaks;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.impl.combat.GravitonBeamEffect.GravitonBeamDamageTakenMod;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.TimeoutTracker;
import com.fs.starfarer.loading.specs.N;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

public class PseudoparticleJetEffect extends com.fs.starfarer.api.impl.combat.dweller.PseudoparticleJetEffect {
	/** Factor to which target max speed is scaled. Does not stack between several Jet hits. */
	public static final float FACTOR_SLOW_DOWN = .8f;
	/** <p>Distance pulled when puller and pullee have the same mass.</p>
	 *  <p>When changing, ensure that most ships do not hit {@link #PULL_DISTANCE_MAX}, lest this feature becomes rather meaningless! </p> */
	public static final float FACTOR_PULL_DISTANCE = 3.f;
	/** Ratio of weapon range, to which the target should be drawn. Will also push it away, if already closer! */
	public static final float FACTOR_RANGE_DRAW_IN = .6f;
	/** Shrouded Maw seems to have all weapons at its center!? - It draws things closer than all other ships.<br/>
	 *  => Keep {@code >.7f}!
	 */
	public static final float FACTOR_RANGE_DRAW_IN_MAW = .8f;
	/** If this looks small, remember that <i>these do</i> stack! */
	public static final float PULL_DISTANCE_MAX = 5.f;
	public static final float PULL_DISTANCE_MIN = 2.f;
	public static final String ID_SLOW_DOWN_MODIFIER = "pseudoparticle_jet_max_speed_modifier";

	private final Logger log = Logger.getLogger(this.getClass());

	@Override
	public void onHit(final DamagingProjectileAPI projectile, final CombatEntityAPI target, final Vector2f point,
	                  final boolean shieldHit, final ApplyDamageResultAPI damageResult, final CombatEngineAPI engine) {
		super.onHit(projectile, target, point, shieldHit, damageResult, engine);

		final var weapon = projectile.getWeapon(); // for   modified range
		final var weaponSpec = weapon.getSpec();   // for unmodified refire delay
		if (weaponSpec instanceof N spec) {
			var massPullee = target.getMass();
			var targetHasNoEngines = true;
			final var velocity = target.getVelocity();
			// slow down
			if (target instanceof ShipAPI ship) {
				massPullee = ship.getMassWithModules();
				targetHasNoEngines = ship.getEngineController().isDisabled();
				if (!ship.hasListenerOfClass(PseudoparticleJetSlowDownMod.class))
					ship.addListener(new PseudoparticleJetSlowDownMod(ship));
				ship.getListeners(PseudoparticleJetSlowDownMod.class).stream()
					.findAny().ifPresent(listener -> listener.notifyHit(spec));
			}
			else if (target instanceof MissileAPI missile)
				targetHasNoEngines = missile.getEngineController().isDisabled();
			if (targetHasNoEngines)
				velocity.scale(FACTOR_SLOW_DOWN);
			// draw in
			final var puller = weapon.getShip();
			final var facing = puller.getFacing() + weapon.getArcFacing();
			final var center = weapon.getLocation();
			final var distance = ("shrouded_maw".equals(puller.getHullSpec().getBaseHullId()) ? FACTOR_RANGE_DRAW_IN_MAW : FACTOR_RANGE_DRAW_IN) * weapon.getRange();
			final var destination = new Vector2f(center.x + distance * (float) Math.cos(facing),
			                                     center.y + distance * (float) Math.sin(facing));
			final var location = target.getLocation();
			final var acceleration = new Vector2f(destination.x - location.x, destination.y - location.y);
			acceleration.normalise();
			acceleration.scale(Math.min(Math.max(FACTOR_PULL_DISTANCE * puller.getMassWithModules() / massPullee, PULL_DISTANCE_MIN), PULL_DISTANCE_MAX));
			velocity.translate(acceleration.x, acceleration.y);
		}
		else
			this.log.error(String.format("""
				Cannot apply slow down effect: Weapon Spec does not seem to have Refire Delay.
				\tExpected class: %s,
				\tFound    class: %s,
				\tOriginating ship : %s
				\tOriginating mount: %s
				""", weaponSpec.getClass(), N.class, projectile.getSource().getName(), projectile.getWeapon().getSlot().getId()));
	}

	/** @see GravitonBeamDamageTakenMod */
	private static class PseudoparticleJetSlowDownMod implements AdvanceableListener {
		private final ShipAPI victim;
		private final TimeoutTracker<WeaponSpecAPI> recentHits = new TimeoutTracker<>();

		private PseudoparticleJetSlowDownMod(final ShipAPI victim) { this.victim = victim; }

		private void notifyHit(final N spec) {
			final var refireDelay = spec.getRefireDelay() + .01f; // a little extra to prevent wobbling
			this.recentHits.add(spec, refireDelay, refireDelay);
		}

		@Override
		public void advance(float amount) {
			this.recentHits.advance(amount);
			if (this.recentHits.getItems().isEmpty()) {
				this.victim.removeListener(this);
				this.victim.getMutableStats().getMaxSpeed().unmodify(ID_SLOW_DOWN_MODIFIER);
			}
			else
				this.victim.getMutableStats().getMaxSpeed().modifyMult(ID_SLOW_DOWN_MODIFIER, FACTOR_SLOW_DOWN);
		}
	}
}
