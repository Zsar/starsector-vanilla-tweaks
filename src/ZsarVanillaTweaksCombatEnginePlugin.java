import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEnginePlugin;
import com.fs.starfarer.api.combat.listeners.FleetMemberDeploymentListener;
import com.fs.starfarer.combat.entities.Ship;

import org.apache.log4j.Logger;

import zsar.vanillatweaks.BarrelModeSwitcher;

@SuppressWarnings("unused") // dynamically loaded
public class ZsarVanillaTweaksCombatEnginePlugin implements CombatEnginePlugin {
	private final Logger log = Logger.getLogger(this.getClass());

	@Override
	public void init(final CombatEngineAPI engine) {
		final FleetMemberDeploymentListener addBarrelModeSwitcher = member -> {
			if (member.getShip() instanceof Ship ship)
				ship.addListener(new BarrelModeSwitcher(ship, this.log));
		};
		engine.getListenerManager().addListener(addBarrelModeSwitcher);
	}
}
