package zsar.vanillatweaks;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.PKDefenderPluginImpl;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageGenFromSeed;

import java.util.Random;

public class PKDefenderPlugin extends PKDefenderPluginImpl {
	@Override
	public void modifyFleet(SalvageGenFromSeed.SDMParams p, CampaignFleetAPI fleet, Random random, boolean withOverride) {
		super.modifyFleet(p, fleet, random, withOverride);

		for (final var curr : fleet.getFleetData().getMembersListCopy()) {
			curr.getVariant().addTag(Tags.VARIANT_ALWAYS_RECOVERABLE);
			curr.getVariant().removeTag(Tags.TAG_RETAIN_SMODS_ON_RECOVERY);
			curr.getVariant().removeTag(Tags.VARIANT_UNRESTORABLE);
		}
	}
}
