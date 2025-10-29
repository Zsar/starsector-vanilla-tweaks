package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.GenericPluginManagerAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.campaign.procgen.themes.PKDefenderPluginImpl;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.loading.specs.N;

import com.thoughtworks.xstream.XStream;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.ArrayList;

@SuppressWarnings("unused") // dynamically loaded
public class ZsarVanillaTweaksModPlugin extends BaseModPlugin {
	private final Logger log;

	public ZsarVanillaTweaksModPlugin() {
		this.log = LogManager.getLogger(this.getClass());
	}

	@Override
	public void configureXStream(XStream x) {
		x.alias("PKDefenderPluginImpl", PKDefenderPlugin.class);
	}

	@Override
	public void onApplicationLoad() {
		this.linkBarrels();
	}

	@Override
	public void onGameLoad(boolean newGame) {
		final GenericPluginManagerAPI plugins = Global.getSector().getGenericPlugins();
		// Janino cannot Method References ~~ -Zsar 2025-10-29
		for (final GenericPluginManagerAPI.GenericPlugin plugin : plugins.getPluginsOfClass(PKDefenderPluginImpl.class))
			plugins.removePlugin(plugin);
		plugins.addPlugin(new PKDefenderPlugin(), true);
	}

	@Override
	public void onNewGame() {
		final SectorAPI sector = Global.getSector();
		final MarketAPI market = sector.getEconomy().getMarket("derinkuyu_market");
		final SectorEntityToken station = sector.getEntityById("derinkuyu_station");
		this.shortenNameOfDerinkuyuStation(market, station);
	}

	private void linkBarrels() {
		try {
			final N.o barrelMode = N.o.valueOf("LINKED");
			for (final WeaponSpecAPI weaponSpec : Global.getSettings().getAllWeaponSpecs()) {
				final int countBarrels = weaponSpec.getTurretAngleOffsets().size();
				if ( 1 < countBarrels
				 &&  WeaponAPI.WeaponType.MISSILE != weaponSpec.getType()
				 && !weaponSpec.getAIHints().contains(WeaponAPI.AIHints.PD)
				 && !"Spread Pattern".equals(weaponSpec.getAccuracyStr()) // looks horrible
				 &&  weaponSpec instanceof N
				 &&  barrelMode != ((N) weaponSpec).getMode()) {
					final N spec = (N) weaponSpec;
					spec.setMode(barrelMode);
					if (1 == spec.getBurstSize()) // e.g. Hephaestus Assault Gun - no burst size to decrease here
						spec.setRefireDelay(spec.getRefireDelay() * countBarrels);
					else                          // e.g. Heavy Autocannon - game multiplies burst size with barrel count on BarrelMode.LINKED, so we need to de-multiply it again
						spec.setBurstSize((int) Math.ceil((float) spec.getBurstSize() / (float) countBarrels));
				}
			}
		}
		catch (final Throwable e) {
			final ArrayList<String> barrelModeNames = new ArrayList<String>(N.o.values().length);
			for (final N.o barrelMode : N.o.values())
				barrelModeNames.add(barrelMode.name());
			this.log.error(String.format("Expected to find Barrel Mode 'LINKED' but only found %s", barrelModeNames), e);
		}
	}

	/** In-game the station is consistently referred to as "Derinkuyu Station", only the UI calls it "Derinkuyu Mining Station". */
	private void shortenNameOfDerinkuyuStation(final MarketAPI market, final SectorEntityToken station) {
		final String shortName = "Derinkuyu Station";
		final String wastefulQualifier = " Mining ";
		if (market.getName().contains(wastefulQualifier)) {
			market.setName(shortName);
		}
		else {
			this.log.warn("Derinkuyu Station market name no longer has superfluous 'Mining' qualifier. Check whether fix 'shortenNameOfDerinkuyuStation' can be removed or simplified.");
		}
		if (station.getName().contains(wastefulQualifier)) {
			station.setName(shortName);
		}
		else {
			this.log.warn("Derinkuyu Station name no longer has superfluous 'Mining' qualifier. Check whether fix 'shortenNameOfDerinkuyuStation' can be removed or simplified.");
		}
	}
}
