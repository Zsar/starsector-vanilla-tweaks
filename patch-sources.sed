# allow up to four Stable Locations
s/num < 2/num < 4/
# make Stable Location blocker flag expire after one in-game year
s/\(\(\s*\).*ADDED_KEY =.*$\)/\1\2public static final float STABLE_LOCATION_CREATION_BLOCKED_DAYS = 365.f;/
s/\.set(ADDED_KEY, true)/.set(ADDED_KEY, true, STABLE_LOCATION_CREATION_BLOCKED_DAYS)/
s/planet\.getMemoryWithoutUpdate()\.getBoolean(ADDED_KEY)/planet.getMemory().getBoolean(ADDED_KEY)/g
