#!/bin/sh
# Builds a TweakSwitch jar for every supported Minecraft version into build/release/.
# One jar per version family: 1.21.10 covers 1.21.9-1.21.10, 26.1 covers 26.1-26.1.2.
# Add a version by adding versions/<mc>.properties.
set -e
cd "$(dirname "$0")/.."
rm -rf build/release
mkdir -p build/release
for mc in 1.21.10 1.21.11 26.1 26.2 26.3; do
	echo "== Minecraft $mc"
	rm -f build/libs/tweakswitch-*.jar
	./gradlew build -Pmc="$mc" --console=plain -q
	cp build/libs/tweakswitch-*+"$mc".jar build/release/
done
ls -l build/release
