package zsar.vanillatweaks;

import com.fs.starfarer.api.impl.campaign.econ.CommodityIconCounts;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class BlackMarketPlugin extends com.fs.starfarer.api.impl.campaign.submarkets.BlackMarketPlugin {
	public static float fractionCombatShips(final boolean military) {
		return military ? 70.f : 0.f;
	}

	public float[] fractions() {
		var goodProductionCombat    = 0;
		var goodProductionFreighter = 0;
		var goodProductionTanker    = 0;
		var goodProductionTransport = 0; // Note: specifically *Troop* Transports
		var goodProductionLiner     = super.market.getSize() - 1;
		var goodProductionUtility   = 0; // Note: defined in data/world/factions/default_ship_roles.json as "utility"
		var commodities = super.market.getAllCommodities();
		for (var commodity : commodities) {
			final var id = commodity.getId();
			final var data = new CommodityIconCounts(commodity);
			final var exportTargetCurrent = data.production - data.demandMetWithLocal;
			final var importTargetCurrent = data.demand     - data.demandMetWithLocal;
			// how much of this commodity travels through space, in 10^x units
			final var throughputCurrent = Math.max(0, Math.max(exportTargetCurrent, importTargetCurrent));
			final var throughputMaximal = Math.abs(commodity.getMaxDemand() - commodity.getMaxSupply());
			if (throughputMaximal > 0) {
				// one ship per unit, and one ship again per unit disrupted, to account for "stranded" truckers waiting for goods
				final var fromTrade = 2 * throughputMaximal - throughputCurrent;
				if (!(commodity.isFuel() || commodity.isPersonnel() || commodity.isMeta() || commodity.isNonEcon()))
					goodProductionFreighter += fromTrade;
				else if (commodity.isFuel())
					goodProductionTanker    += fromTrade;
				if (commodity.getCommodity().hasTag(Commodities.MARINES))
					goodProductionTransport += fromTrade;
				if (Commodities.SHIPS.equals(commodity.getId())) {
					goodProductionCombat    += fromTrade;
					goodProductionFreighter += fromTrade;
					goodProductionTanker    += fromTrade;
					goodProductionTransport += fromTrade;
					goodProductionLiner     += fromTrade;
					goodProductionUtility   += fromTrade;
				}
			}
		}
		if (super.market.getIndustries().stream().anyMatch(industry -> {
			var id = industry.getId();
			return industry.getSpec().hasTag(Industries.TAG_BATTLESTATION) && !industry.isDisrupted() // feels odd to expect them on the market, while the station is disrupted
			    || Industries.TECHMINING.equals(id) || Industries.WAYSTATION.equals(id);              // - one would think they're out and about helping to clean up
		}))
			goodProductionUtility += Math.max(1, super.market.getSize() - 2); // at least one, but overall rather few
		return new float[] {
			super.itemGenRandom.nextFloat() * goodProductionCombat,
			super.itemGenRandom.nextFloat() * goodProductionFreighter,
			super.itemGenRandom.nextFloat() * goodProductionTanker,
			super.itemGenRandom.nextFloat() * goodProductionTransport,
			super.itemGenRandom.nextFloat() * goodProductionLiner,
			super.itemGenRandom.nextFloat() * goodProductionUtility,
		};
	}

	@Override
	public void updateCargoPrePlayerInteraction() {
		if (super.okToUpdateShipsAndWeapons()) {
			final float stability = market.getStabilityValue();

			final boolean military = Misc.isMilitary(market); // match begin

			final var factionPicker = new WeightedRandomPicker<String>();
			factionPicker.add(market.getFactionId(), 15f - stability);
			factionPicker.add(submarket.getFaction().getId(), 6f);

			int weapons = 6 + Math.max(0, market.getSize() - 1) + (military ? 5 : 0);
			int fighters = 2 + Math.max(0, (market.getSize() - 3) / 2) + (military ? 2 : 0);
			weapons = 6 + Math.max(0, market.getSize() - 1);
			fighters = 2 + Math.max(0, (market.getSize() - 3) / 2);

			addWeapons(weapons, weapons + 2, 3, factionPicker);
			addFighters(fighters, fighters + 2, 3, factionPicker);

			if (military) {
				weapons = market.getSize();
				fighters = Math.max(1, market.getSize() / 3);
				addWeapons(weapons, weapons + 2, 3, market.getFactionId(), false);
				addFighters(fighters, fighters + 2, 3, market.getFactionId());
			}

			float sMult = 0.5f + Math.max(0, (1f - stability / 10f)) * 0.5f;
			getCargo().getMothballedShips().clear();

			final var doctrine = super.market.getFaction().getDoctrine().clone();
			final var combatShipFraction = fractionCombatShips(military);
			final var fractions = fractions();
			super.addShips(super.market.getFactionId(),
				combatShipFraction + fractions[0],
				fractions[1],
				fractions[2],
				fractions[3],
				fractions[4],
				fractions[5],
				null,
				0.f, // qualityMod
				null,
				doctrine);
			final var doctrineOverride = super.submarket.getFaction().getDoctrine().clone();
			doctrineOverride.setWarships  (Math.max(doctrineOverride.getWarships  (), doctrine.getWarships()));
			doctrineOverride.setPhaseShips(Math.max(doctrineOverride.getPhaseShips(), doctrine.getPhaseShips()));
			doctrineOverride.setCarriers  (Math.max(doctrineOverride.getCarriers  (), doctrine.getCarriers()));
			doctrineOverride.setShipSize  (Math.max(doctrineOverride.getShipSize  (), doctrine.getShipSize()));
			doctrineOverride.setCombatFreighterProbability(1.f);
			super.addShips(super.submarket.getFaction().getId(),
				combatShipFraction + fractions[0],
				fractions[1],
				fractions[2],
				fractions[3],
				fractions[4],
				fractions[5],
				Math.min(1f, Misc.getShipQuality(market, market.getFactionId()) + 0.5f),
				0.f, // qualityMod
				null,
				doctrineOverride,
				3 // no capital ships, max size cruiser
			); // match end

			addHullMods(4, 1 + itemGenRandom.nextInt(3));
		}
	}
}
