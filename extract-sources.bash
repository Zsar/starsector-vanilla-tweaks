#!/bin/bash
# Extraction
planet_interaction_dialog_plugin=com/fs/starfarer/api/impl/campaign/PlanetInteractionDialogPluginImpl.java
unzip -o ../../starfarer.api $planet_interaction_dialog_plugin -d generated-sources
planet_interaction_dialog_plugin=generated-sources/$planet_interaction_dialog_plugin
# Sanity Checks
exit_code=0
stable_location_limit_candidates=$(grep -c 'num < 2' "$planet_interaction_dialog_plugin")
if [ "$stable_location_limit_candidates" != 1 ]; then
	>&2 echo 'PlanetInteractionDialogPluginImpl does not contain exactly one candidate for the Stable Location limit:'\
	"$stable_location_limit_candidates"' have been found.'
	exit_code=$((exit_code | 1))
fi
set_blocker_candidates=$(grep -c '.set(ADDED_KEY, true);' "$planet_interaction_dialog_plugin")
if [ "$set_blocker_candidates" != 1 ]; then
	>&2 echo 'PlanetInteractionDialogPluginImpl does not contain exactly one candidate for setting the Stable Location creation blocker:'\
	"$set_blocker_candidates"' have been found.'
	exit_code=$((exit_code | 2))
fi
blocker_definitions=$(grep -c 'ADDED_KEY =' "$planet_interaction_dialog_plugin")
if [ "$blocker_definitions" != 1 ]; then
	>&2 echo 'PlanetInteractionDialogPluginImpl does not contain exactly one candidate for defining the Stable Location creation blocker:'\
	"$blocker_definitions"' have been found.'
	exit_code=$((exit_code | 4))
fi
blocker_usages=$(grep -c 'ADDED_KEY' "$planet_interaction_dialog_plugin")
get_blocker_candidates=$(grep -c 'planet.getMemoryWithoutUpdate().getBoolean(ADDED_KEY);' "$planet_interaction_dialog_plugin")
if [ $((blocker_definitions + get_blocker_candidates + set_blocker_candidates)) != "$blocker_usages" ]; then
	>&2 echo 'PlanetInteractionDialogPluginImpl contains unaccounted uses of the Stable Location creation blocker. Found:'\
	"$blocker_definitions"' definitions'\
	"$get_blocker_candidates"' getter'\
	"$set_blocker_candidates"' setter'\
	"$blocker_usages"' total accesses'
	exit_code=$((exit_code | 8))
fi
[ $exit_code == 0 ] || exit $exit_code
# Patching
echo 'Patching '"$planet_interaction_dialog_plugin"
sed --file=patch-sources.sed --in-place "$planet_interaction_dialog_plugin"
