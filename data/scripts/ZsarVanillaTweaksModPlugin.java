package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

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
		// as per Station Commander: "The miners of Derinkuyu Station, which is also located in this system and was operating near a subsistence level [...]"
		this.addSubsistenceMiningToDerinkuyuStation(market);
		this.shortenNameOfDerinkuyuStation(market, station);
	}

	/** Subsistence may be little, but it is more than nothing. */
	private void addSubsistenceMiningToDerinkuyuStation(final MarketAPI market) {
		final List<MutableCommodityQuantity> supply = market.getIndustry(Industries.MINING).getAllSupply();
		if (supply.isEmpty()) {
			final String oreId = market.addCondition(Conditions.ORE_SPARSE);
			final MarketConditionAPI ore = market.getSpecificCondition(oreId);
			ore.setSurveyed(true);
		}
		else {
			final List<String> existingProducts = new ArrayList<String>(supply.size());
			for (final MutableCommodityQuantity product : supply) {
				existingProducts.add(product.getSpec().getName());
			}
			this.log.warn(String.format("Derinkuyu Station mining already produces %s. Check whether fix 'addSubsistenceMiningToDerinkuyuStation' can be removed.", existingProducts));
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
