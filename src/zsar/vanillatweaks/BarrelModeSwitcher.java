package zsar.vanillatweaks;

import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponGroupAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.combat.entities.Ship;
import com.fs.starfarer.loading.specs.N;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class BarrelModeSwitcher implements AdvanceableListener {
	private final N.o barrelModeAlternating = N.o.valueOf("ALTERNATING");
	private final N.o barrelModeLinked      = N.o.valueOf("LINKED");

	private final Ship ship;

	private final GroupValues[] values;

	public BarrelModeSwitcher(final Ship ship, final Logger log) {
		this.ship = ship;

		final var countGroups = ship.getGroups().size();
		this.values = new GroupValues[countGroups];
		int i = 0;
		for (final var group : this.ship.getGroups()) {
			if (group != this.ship.getWeaponGroup(i)) // cannot check it via reflection -Zsar 2025-11-03
				log.error("Weapon groups are no longer guaranteed to be ordered! BarrelModeSwitcher must be adjusted!");
			this.values[i] = new GroupValues(group.getWeapons().stream()
			                                 	.filter(this::filterToggleable)
			                                 	.peek(WeaponAPI::ensureClonedSpec)
			                                 	.collect(Collectors.toCollection(ArrayList::new)),
			                                 group.isAutofiring());
			this.toggleWeaponModes(group, this.values[i++]);
		}
	}

	@Override
	public void advance(final float unused) {
		int i = 0;
		for (final var group : this.ship.getGroups()) {
			final GroupValues values = this.values[i++];
			if (values.doesAutofiringRequireAction(group.isAutofiring()))
				this.toggleWeaponModes(group, values);
		}
	}

	private boolean filterToggleable(final WeaponAPI weapon) {
		final var spec = weapon.getOriginalSpec();
		return  1 < spec.getTurretAngleOffsets().size()
			&&  WeaponAPI.WeaponType.MISSILE != spec.getType()
			//&& !spec.getAIHints().contains(WeaponAPI.AIHints.PD) // actually let's try this
			//&& !"Spread Pattern".equals(spec.getAccuracyStr()) // looks horrible // actually let's try this
			&&  spec instanceof N;
	}

	private void toggleWeaponModes(final WeaponGroupAPI group, final GroupValues values) {
		if (group.isAutofiring())
			for (final var weapon : values.weaponsTogglable) {
				final var targetShip = group.getAutofirePlugin(weapon).getTargetShip();
				if (targetShip != null && !targetShip.isFighter())
					this.toggleToLinked(weapon);
				else
					this.toggleToNonLinked(weapon);
			}
		else
			values.weaponsTogglable.forEach(this::toggleToLinked);
	}

	private void toggleToLinked(final WeaponAPI weapon) {
		final var specCurrent = (N) weapon.getSpec();
			final var id = weapon.getId();
			final var barrelMode = specCurrent.getMode();
			final var burstSize = specCurrent.getBurstSize();
			final var refireDelay = specCurrent.getRefireDelay();
		if (specCurrent.getMode() == this.barrelModeLinked)
			return;

		final var specOriginal = (N) weapon.getOriginalSpec();
		if (specOriginal.getMode() != this.barrelModeLinked) {
			final var countBarrels = specOriginal.getTurretAngleOffsets().size();
			specCurrent.setMode(this.barrelModeLinked);
			if (1 == specOriginal.getBurstSize()) {
				// e.g. Hephaestus Assault Gun - no burst size to decrease here
					final var newRefireDelay = specOriginal.getRefireDelay() * countBarrels;
				specCurrent.setRefireDelay(newRefireDelay);
			}
			else {
				// e.g. Heavy Autocannon - game multiplies burst size with barrel count on BarrelMode.LINKED, so we need to de-multiply it again
					final var newBurstSize = (int) Math.ceil((float) specOriginal.getBurstSize() / (float) countBarrels);
				specCurrent.setBurstSize(newBurstSize);
			}
		}
		else {
			specCurrent.setBurstSize(specOriginal.getBurstSize());
			specCurrent.setMode(specOriginal.getMode());
			specCurrent.setRefireDelay(specOriginal.getRefireDelay());
		}
	}

	private void toggleToNonLinked(final WeaponAPI weapon) {
		final var specCurrent = (N) weapon.getSpec();
			final var id = weapon.getId();
			final var barrelMode = specCurrent.getMode();
			final var burstSize = specCurrent.getBurstSize();
			final var refireDelay = specCurrent.getRefireDelay();
		if (specCurrent.getMode() != this.barrelModeLinked)
			return;

		final var specOriginal = (N) weapon.getOriginalSpec();
		if (specOriginal.getMode() == this.barrelModeLinked) {
			final var countBarrels = specOriginal.getTurretAngleOffsets().size();
			specCurrent.setMode(this.barrelModeAlternating);
			if (1 == specOriginal.getBurstSize()) {
				// e.g. Hephaestus Assault Gun - no burst size to increase here
					final var newRefireDelay = specOriginal.getRefireDelay() / countBarrels;
				specCurrent.setRefireDelay(newRefireDelay);
			}
			else {
				// e.g. Heavy Autocannon - game multiplies burst size with barrel count on BarrelMode.LINKED, so we need to do so as well
					final var newBurstSize = specOriginal.getBurstSize() * countBarrels;
				specCurrent.setBurstSize(newBurstSize);
			}
		}
		else {
			specCurrent.setBurstSize(specOriginal.getBurstSize());
			specCurrent.setMode(specOriginal.getMode());
			specCurrent.setRefireDelay(specOriginal.getRefireDelay());
		}
	}

	private static class GroupValues {
		private final ArrayList<WeaponAPI> weaponsTogglable;
		private boolean wasAutofiring;

		private GroupValues(final ArrayList<WeaponAPI> weaponsTogglable, boolean wasAutofiring) {
			this.weaponsTogglable = weaponsTogglable;
			this.wasAutofiring = wasAutofiring;
		}

		private boolean doesAutofiringRequireAction(final boolean isAutofiring) {
			final var changed = isAutofiring || this.wasAutofiring;
			this.wasAutofiring = isAutofiring;
			return changed;
		}
	}
}
