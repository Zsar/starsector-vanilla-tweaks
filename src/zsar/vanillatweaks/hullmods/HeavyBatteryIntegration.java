package zsar.vanillatweaks.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.hullmods.HeavyBallisticsIntegration;

public class HeavyBatteryIntegration extends HeavyBallisticsIntegration {
	@Override
	public void applyEffectsBeforeShipCreation(final HullSize hullSize, final MutableShipStatsAPI stats, final String id) {
		final var stat = stats.getDynamic();
		stat.getMod(Stats.LARGE_BALLISTIC_MOD).modifyFlat(id, -HeavyBallisticsIntegration.COST_REDUCTION);
		stat.getMod(   Stats.LARGE_ENERGY_MOD).modifyFlat(id, -HeavyBallisticsIntegration.COST_REDUCTION);
	}
}
