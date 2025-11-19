package zsar.vanillatweaks;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.HasMemory;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.campaign.PlanetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.procgen.themes.PKDefenderPluginImpl;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.loading.WingRole;
import com.fs.starfarer.loading.specs.g;
import com.fs.starfarer.loading.specs.N;

import com.thoughtworks.xstream.XStream;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.regex.Pattern;

@SuppressWarnings("unused") // dynamically loaded
public class ModPlugin extends BaseModPlugin {
	private final Logger log;

	public ModPlugin() {
		this.log = LogManager.getLogger(this.getClass());
	}

	@Override
	public void configureXStream(XStream x) {
		x.alias("PKDefenderPluginImpl", PKDefenderPlugin.class);
	}

	@Override
	public void onApplicationLoad() {
		final var settings = Global.getSettings();

		for (final var role : WingRole.values()) {
			final var pattern = Pattern.compile(role.name(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			for (final var wing : settings.getAllFighterWingSpecs())
				if (wing.getRole() != role && pattern.matcher(wing.getRoleDesc()).find())
					this.alignWingRole(wing, role);
		}

		this.adjustAiHints(settings);
		this.denerfLionsGuardShips(settings);
		this.linkBarrels();
	}

	@Override
	public void onGameLoad(boolean newGame) {
		final var sector = Global.getSector();
		this.expireSectorLocationCreationBlocker(sector);

		final var plugins = sector.getGenericPlugins();
		plugins.getPluginsOfClass(PKDefenderPluginImpl.class).forEach(plugins::removePlugin);
		plugins.addPlugin(new PKDefenderPlugin(), true);
	}

	@Override
	public void onNewGame() {
		final var sector = Global.getSector();
		this.shortenNameOfDerinkuyuStation(sector.getEconomy().getMarket("derinkuyu_market"),
		                                   sector.getEntityById("derinkuyu_station"));
	}

	/** <p>{@link WeaponAPI.AIHints#CONSERVE_ALL} makes the AI more reluctant to fire a weapon, no questions asked;
	 *     if another AIHint already makes the AI more reluctant to fire it <i>in specific use cases</i>,
	 *     this is harmful, as it will lead to missed opportunities.</p>
	 *  <p>{@link WeaponAPI.AIHints#USE_LESS_VS_SHIELDS} was introduced after {@link WeaponAPI.AIHints#CONSERVE_FOR_ANTI_ARMOR},
	 *     but has not been added to older weapons. Adding it to weapons limited by ammunition keeps them ready to exploit opportunities.</p>
	 */
	private void adjustAiHints(final SettingsAPI settings) {
		final var conserve = Pattern.compile("CONSERVE(?!_ALL)");
		final var logStatementPreamble = "Adjusted weapon '%s' AIHints:";
		final var hintsSufficient = EnumSet.allOf(WeaponAPI.AIHints.class);
		hintsSufficient.removeIf(hint -> !conserve.matcher(hint.name()).find());
		hintsSufficient.add(WeaponAPI.AIHints.USE_LESS_VS_SHIELDS); // sadly escapes the naming scheme
		for (final var weapon : settings.getActuallyAllWeaponSpecs()) {
			final var hints = weapon.getAIHints();
			var logStatement = logStatementPreamble;
			if (hints.contains(WeaponAPI.AIHints.CONSERVE_FOR_ANTI_ARMOR) && weapon.usesAmmo() && hints.add(WeaponAPI.AIHints.USE_LESS_VS_SHIELDS))
				logStatement += String.format("\n\t+'%s' as it already has '%s' and also uses ammunition.",
				                              WeaponAPI.AIHints.USE_LESS_VS_SHIELDS,
				                              WeaponAPI.AIHints.CONSERVE_FOR_ANTI_ARMOR);
			if (!Collections.disjoint(hints, hintsSufficient) && hints.remove(WeaponAPI.AIHints.CONSERVE_ALL))
				logStatement += String.format("\n\t-'%s' as it already has a more specific restriction.",
				                              WeaponAPI.AIHints.CONSERVE_ALL);
			if (!logStatementPreamble.equals(logStatement))
				this.log.info(String.format(logStatement, weapon.getWeaponId()));
		}
	}

	private void alignWingRole(final FighterWingSpecAPI misgendered, final WingRole right) {
		final var wrong = misgendered.getRole();
		final var tagRight = tagFrom(right);
		final var tagWrong = tagFrom(wrong);
		final var tags = misgendered.getTags();
		final var tagsRight = new LinkedHashSet<String>();
		for (final var i = tags.iterator(); i.hasNext(); ) {
			final var value = i.next();
			if (value.startsWith(tagWrong)) {
				i.remove();
				tagsRight.add(value.replace(tagWrong, tagRight));
			}
		}
		tags.addAll(tagsRight);
		misgendered.setRole(right);
		this.log.info(String.format("Aligned wing '%s' from '%s' to '%s'", misgendered.getId(), wrong, right));
	}

	/** <p>As per the original blog post, they should pay only for Solar Shielding and nothing more.</p>
	 *  <p>The Executor also gains shield efficiency, but none of the others do.</p>
	 */
	private void denerfLionsGuardShips(final SettingsAPI settings) {
		final var executor = settings.getHullSpec("executor");
		final var fluxPerDamageExecutor = executor.getShieldSpec().getFluxPerDamageAbsorbed();
		final var fluxPerDamageExecutorBase = executor.getBaseHull().getShieldSpec().getFluxPerDamageAbsorbed();
		final var fluxPerDamageOffset = fluxPerDamageExecutorBase - fluxPerDamageExecutor;
		if (fluxPerDamageExecutor >= fluxPerDamageExecutorBase)
			this.log.warn(String.format("""
				Executor no longer seems to receive a flux per damage buff compared to its base hull. Adapt method!
				\tflux per damage Executor : %f.2
				\tflux per damage base hull: %f.2
				\tflux per damage offset: %f.2
				""", fluxPerDamageExecutor, fluxPerDamageExecutorBase, fluxPerDamageOffset));
		final var solarShielding = settings.getHullModSpec("solar_shielding");
		final var idSolarShielding = solarShielding.getId();
		final var ships = settings.getAllShipHullSpecs();
		ships.stream()
			.filter(ship -> ship instanceof g)
			.filter(ship -> !ship.isBaseHull())
			.filter(ship -> "Lion's Guard".equals(ship.getManufacturer()))
			.map(g.class::cast)
			.forEach(ship -> {
				final var base = ship.getBaseHull();
				final var id = ship.getHullId();
				if (ship.getBuiltInMods().contains(idSolarShielding)) {
					final var opBase = base.getOrdnancePoints(null);
					final var opShip = ship.getOrdnancePoints(null);
					final var opExpected = opBase - solarShielding.getCostFor(ship.getHullSize());
					if (opShip < opExpected)
						ship.setOrdnancePoints(opExpected);
					else if (opShip > opExpected)
						this.log.warn(String.format("""
							Lion's Guard ship with more than expected OP found. Validate filter!
							\tId: %s
							\tOP expected: %d
							\tOP found   : %d
							""", id, opExpected, opShip));
				}
				else
					this.log.warn(String.format("""
						Lion's Guard ship without Solar Shielding found. Skipping!
						\tId: %s
						""", id));

				final var shield = ship.getShieldSpec();
				final var fluxPerDamageShieldBase = base.getShieldSpec().getFluxPerDamageAbsorbed();
				final var fluxPerDamageShieldShip = shield.getFluxPerDamageAbsorbed();
				final var fluxPerDamageShieldExpected = fluxPerDamageShieldBase - fluxPerDamageOffset;
				if (fluxPerDamageShieldShip > fluxPerDamageShieldExpected)
					shield.setFluxPerDamageAbsorbed(fluxPerDamageShieldExpected);
				else if (fluxPerDamageShieldShip < fluxPerDamageShieldExpected)
					this.log.warn(String.format("""
						Lion's Guard ship with more efficient shields than expected found. Validate filter!
						\tId: %s
						\tFlux per damage expected: %f.2
						\tFlux per damage found   : %f.2
						""", id, fluxPerDamageShieldExpected, fluxPerDamageShieldShip));
			});
	}

	/** retrofit an expiry date to stars manipulated before this mod version was present */
	private void expireSectorLocationCreationBlocker(final SectorAPI sector) {
		final var sectorMemory = sector.getMemoryWithoutUpdate();
		final var myTypeName = this.getClass().getTypeName();
		if (!sectorMemory.is(PlanetInteractionDialogPluginImpl.ADDED_KEY, myTypeName)) {
			var count = new Pointer<>(0);
			sector.getStarSystems().stream()
				.map(StarSystemAPI::getStar)
				.filter(Objects::nonNull) // Alpha Site, nebulae
				.map(HasMemory::getMemoryWithoutUpdate)
				.filter(memory -> memory.contains(PlanetInteractionDialogPluginImpl.ADDED_KEY))
				.filter(memory -> 0.f > memory.getExpire(PlanetInteractionDialogPluginImpl.ADDED_KEY))
				.peek(unused -> ++count.value)
				.forEach(memory -> memory.expire(PlanetInteractionDialogPluginImpl.ADDED_KEY,
				                                 PlanetInteractionDialogPluginImpl.STABLE_LOCATION_CREATION_BLOCKED_DAYS));
			sectorMemory.set(PlanetInteractionDialogPluginImpl.ADDED_KEY, myTypeName);
			this.log.info(String.format("Added expiry to %d stars which were previously blocked from receiving additional Stable Locations.", count.value));
		}
	}

	private void linkBarrels() {
		try {
			final var barrelMode = N.o.valueOf("LINKED");
			for (final WeaponSpecAPI weaponSpec : Global.getSettings().getAllWeaponSpecs()) {
				final var countBarrels = weaponSpec.getTurretAngleOffsets().size();
				if ( 1 < countBarrels
				 &&  WeaponAPI.WeaponType.MISSILE != weaponSpec.getType()
				 && !weaponSpec.getAIHints().contains(WeaponAPI.AIHints.PD)
				 && !"Spread Pattern".equals(weaponSpec.getAccuracyStr()) // looks horrible
				 &&  weaponSpec instanceof N spec
				 &&  barrelMode != spec.getMode()) {
					spec.setMode(barrelMode);
					if (1 == spec.getBurstSize()) // e.g. Hephaestus Assault Gun - no burst size to decrease here
						spec.setRefireDelay(spec.getRefireDelay() * countBarrels);
					else                          // e.g. Heavy Autocannon - game multiplies burst size with barrel count on BarrelMode.LINKED, so we need to de-multiply it again
						spec.setBurstSize((int) Math.ceil((float) spec.getBurstSize() / (float) countBarrels));
				}
			}
		}
		catch (final Throwable e) {
			final var barrelModeNames = Arrays.stream(N.o.values()).map(Enum::name).toList();
			this.log.error(String.format("Expected to find Barrel Mode 'LINKED' but only found %s", barrelModeNames), e);
		}
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

	private static String tagFrom(final WingRole role) {
		return role.name().toLowerCase();
	}
}
