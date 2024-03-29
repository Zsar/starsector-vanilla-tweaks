package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

@SuppressWarnings("unused") // dynamically loaded
public class ZsarVanillaTweaksModPlugin extends BaseModPlugin {
	private final Logger log;

	public ZsarVanillaTweaksModPlugin() {
		this.log = LogManager.getLogger(this.getClass());
	}

	@Override
	public void onNewGame() {
		final SectorAPI sector = Global.getSector();
		final MarketAPI market = sector.getEconomy().getMarket("derinkuyu_market");
		final SectorEntityToken station = sector.getEntityById("derinkuyu_station");
		this.shortenNameOfDerinkuyuStation(market, station);
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
