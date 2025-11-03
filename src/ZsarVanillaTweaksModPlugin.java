import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.procgen.themes.PKDefenderPluginImpl;

import com.thoughtworks.xstream.XStream;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import zsar.vanillatweaks.PKDefenderPlugin;

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
	public void onGameLoad(boolean newGame) {
		final var plugins = Global.getSector().getGenericPlugins();
		plugins.getPluginsOfClass(PKDefenderPluginImpl.class).forEach(plugins::removePlugin);
		plugins.addPlugin(new PKDefenderPlugin(), true);
	}

	@Override
	public void onNewGame() {
		final var sector = Global.getSector();
		this.shortenNameOfDerinkuyuStation(sector.getEconomy().getMarket("derinkuyu_market"),
		                                   sector.getEntityById("derinkuyu_station"));
	}

	/** In-game the station is consistently referred to as "Derinkuyu Station", only the UI calls it "Derinkuyu Mining Station". */
	private void shortenNameOfDerinkuyuStation(final MarketAPI market, final SectorEntityToken station) {
		final var shortName = "Derinkuyu Station";
		final var wastefulQualifier = " Mining ";
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
