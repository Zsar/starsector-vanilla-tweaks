# Dependencies
- GNU bash (tested with version 5.3.3)
- GNU sed (tested with version 4.9)
# Build
1. execute [extract-sources.bash](extract-sources.bash) to create generated-sources
2. build jar from generated-sources and [src](src)
# Increment version
- execute [increment-version.bash](increment-version.bash) with the new version in [Semantic Versioning format](https://semver.org/#summary) (`<major>[.<minor>[.<patch>]]`) 
