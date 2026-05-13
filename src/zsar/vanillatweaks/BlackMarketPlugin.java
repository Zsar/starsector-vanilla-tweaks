package zsar.vanillatweaks;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class BlackMarketPlugin extends com.fs.starfarer.api.impl.campaign.submarkets.BlackMarketPlugin {
	public static float pOther = .1f;

	public enum Proliferation {
		NONE(0.f),
		SOME(5.f),
		MUCH(2.f * SOME.value)
		;
		public final float value;

		Proliferation(final float value) { this.value = value; }

		public static Proliferation max(final Proliferation a, final Proliferation b) {
			return Math.max(a.value, b.value) == a.value ? a : b;
		}
	}

	public static Proliferation fromGoodConsumption(final Proliferation soFar, final Industry consumer) {
		return Proliferation.max(!consumer.isBuilding() || consumer.isUpgrading()
		                         	? Proliferation.SOME : Proliferation.NONE,
		                         soFar);
	}

	public static Proliferation fromGoodProduction(final Proliferation soFar, final Industry producer) {
		return Proliferation.max(producer.isBuilding() && !producer.isUpgrading()
		                         	? Proliferation.NONE
		                         	: null == producer.getSpecialItem()
		                         	? Proliferation.SOME
		                         	: Proliferation.MUCH,
		                         soFar);
	}

	public static Proliferation fromShipProduction(final Proliferation soFar, final Industry producer) {
		return Proliferation.MUCH == soFar || producer.isFunctional()
		     ? Proliferation.MUCH : Proliferation.NONE;
	}

	/** If the industry that nominally needs these ships is disrupted, increase Black Market supply by one step. */
	public static Proliferation blackMarketSpecial(final Proliferation soFar, final Industry producer) {
		return Proliferation.MUCH == soFar || !producer.isDisrupted()
		     ? soFar
		     : Proliferation.SOME == fromGoodProduction(soFar, producer)
		     ? Proliferation.MUCH
		     : Proliferation.SOME;
	}

	public static boolean demands(final Industry industry, final String commodity) {
		return industry.getDemand(commodity).getQuantity().isPositive();
	}

	public static boolean supplies(final Industry industry, final String commodity) {
		return industry.getSupply(commodity).getQuantity().isPositive();
	}

	public float fraction(final Proliferation ofRelevantCommodity) {
		return (super.itemGenRandom.nextFloat() <= pOther ? Proliferation.MUCH : ofRelevantCommodity).value;
	}

	public static float fractionCombatShips(final boolean military) {
		return military ? 70.f : 10.f;
	}

	public float[] fractionsNonCombatShips() {
		var goodProductionFreighter = Proliferation.NONE;
		var goodProductionTanker    = Proliferation.NONE;
		var goodProductionTransport = Proliferation.NONE; // Note: specifically *Troop* Transports
		var goodProductionLiner     = super.market.getSize() > 6 // at least 10'000'000 inhabitants
		                            ? Proliferation.MUCH
		                            : super.market.getSize() > 3 // at least     10'000 inhabitants
		                            ? Proliferation.SOME
		                            : Proliferation.NONE;
		var goodProductionUtility   = Proliferation.NONE; // Note: defined in data/world/factions/default_ship_roles.json as "utility"
		var shipProduction = Proliferation.NONE;
		for (final var industry : super.market.getIndustries()) {
			if (industry.isIndustry())
				goodProductionFreighter = fromGoodConsumption(goodProductionFreighter, industry);
			if (industry.isIndustry() || Industries.MEGAPORT.equals(industry.getId()))
				goodProductionFreighter = blackMarketSpecial(goodProductionFreighter, industry);
			if (demands(industry , Commodities.FUEL))
				goodProductionTanker = fromGoodConsumption(goodProductionTanker, industry);
			if (supplies(industry, Commodities.FUEL))
				goodProductionTanker = blackMarketSpecial(goodProductionTanker, industry);
			if (demands(industry , Commodities.MARINES))
				goodProductionTransport = fromGoodConsumption(goodProductionTransport, industry);
			if (supplies(industry, Commodities.MARINES))
				goodProductionTransport = blackMarketSpecial(goodProductionTransport, industry);
			if (Industries.TECHMINING.equals(industry.getId()))
				goodProductionUtility = blackMarketSpecial(goodProductionUtility, industry);
			if (supplies(industry, Commodities.SHIPS))
				shipProduction = fromShipProduction(shipProduction, industry);
		}
		return Proliferation.NONE != shipProduction ? new float[] {
		                                              	shipProduction.value,
		                                              	shipProduction.value,
		                                              	shipProduction.value,
		                                              	shipProduction.value,
		                                              	shipProduction.value,
		                                              }
		                                            : new float[] {
		                                              	this.fraction(goodProductionFreighter),
		                                              	this.fraction(goodProductionTanker),
		                                              	this.fraction(goodProductionTransport),
		                                              	this.fraction(goodProductionLiner),
		                                              	this.fraction(goodProductionUtility),
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
			final var fractionsNonCombatShips = fractionsNonCombatShips();
			super.addShips(super.market.getFactionId(),
				combatShipFraction,
				fractionsNonCombatShips[0],
				fractionsNonCombatShips[1],
				fractionsNonCombatShips[2],
				fractionsNonCombatShips[3],
				fractionsNonCombatShips[4],
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
				combatShipFraction,
				fractionsNonCombatShips[0],
				fractionsNonCombatShips[1],
				fractionsNonCombatShips[2],
				fractionsNonCombatShips[3],
				fractionsNonCombatShips[4],
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
