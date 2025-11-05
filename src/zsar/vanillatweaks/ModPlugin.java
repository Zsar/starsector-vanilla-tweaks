package zsar.vanillatweaks;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.campaign.procgen.themes.PKDefenderPluginImpl;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.loading.WingRole;
import com.fs.starfarer.loading.specs.N;

import com.thoughtworks.xstream.XStream;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.LinkedHashSet;
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
		for (final var role : WingRole.values()) {
			final var pattern = Pattern.compile(role.name(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			for (final var wing : Global.getSettings().getAllFighterWingSpecs())
				if (wing.getRole() != role && pattern.matcher(wing.getRoleDesc()).find())
					this.alignWingRole(wing, role);
		}

		this.linkBarrels();
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
