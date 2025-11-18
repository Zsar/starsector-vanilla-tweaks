#!/bin/bash
version_parts=(major minor patch)
# new version
IFS='.' read -a version_new <<< "$1"
for i in 0 1 2; do
	[ -z ${version_new[i]} ] && version_new[i]=0
done
# current version
script_get_current='s/^.*"'${version_parts[0]}'": ([[:digit:]]+), "'${version_parts[1]}'": ([[:digit:]]+), "'${version_parts[2]}'": ([[:digit:]]+).*$/\1.\2.\3/p'
IFS='.' read -a version_current <<< "$(sed --regexp-extended --expression "$script_get_current" --quiet zsarVanillaTweaks.version)"
for i in 0 1 2; do
	((version_new[i] > version_current[i])) && increment=${version_parts[i]} && break
	((version_new[i] < version_current[i]))\
		&& error="New ${version_parts[i]} version ${version_new[i]} must not be smaller than current one ${version_current[i]}!"\
		&& break
done
if [ -n "$error" ]; then
	>&2 echo "$error"
	exit 1
elif [ -z "$increment" ]; then
	echo 'New version equals current version!? - Nothing to do.' # no error, just NOP
	exit 0
fi
# update version
script_update='s/("'${version_parts[0]}'": )[[:digit:]]+(, "'${version_parts[1]}'": )[[:digit:]]+(, "'${version_parts[2]}'": )[[:digit:]]+/\1'${version_new[0]}'\2'${version_new[1]}'\3'${version_new[2]}'/'
sed --regexp-extended --expression "$script_update" --in-place mod_info.json
sed --regexp-extended --expression "$script_update" --in-place zsarVanillaTweaks.version
