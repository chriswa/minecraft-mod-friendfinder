#!/bin/zsh
# Build both jars. Installing is opt-in, per side:
#
#   ./build.sh                    build only, into build/
#   ./build.sh --install-client   also copy into the Prism instance's mods/
#   ./build.sh --install-server   also copy into the dedicated server's mods/
#
# Installing the server jar only puts a file on disk. Mods load at startup, so it
# does nothing at all until the server is restarted — by you, when you choose.
# This script never starts, stops or signals the server.
#
# No Gradle and no decompile. We compile against a stub of the client jar carrying
# the names a mod sees at runtime (MCP class names, SRG members), which is exactly
# what a built Forge mod links against — so the output needs no reobfuscation.
set -euo pipefail

HERE="${0:A:h}"
PRISM="$HOME/Library/Application Support/PrismLauncher"
INSTANCE="$PRISM/instances/InfinityEvolved-arm64/.minecraft"      # Prism only — not CurseForge
SERVER="$HOME/Minecraft/dedicated-infinity-evolved-server"
JAVA_HOME="$HOME/Minecraft/java/zulu8.96.0.205-ca-jdk8.0.504-macosx_aarch64/Contents/Home"

FORGE="$PRISM/libraries/net/minecraftforge/forge/1.12.2-14.23.5.2860/forge-1.12.2-14.23.5.2860-universal.jar"
CLIENT="$PRISM/libraries/com/mojang/minecraft/1.12.2/minecraft-1.12.2-client.jar"
ASM="$PRISM/libraries/org/ow2/asm/asm-debug-all/5.2/asm-debug-all-5.2.jar"
NETTY="$PRISM/libraries/io/netty/netty-all/4.1.9.Final/netty-all-4.1.9.Final.jar"
LWJGL="$PRISM/libraries/org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar"
AUTHLIB="$PRISM/libraries/com/mojang/authlib/1.5.25/authlib-1.5.25.jar"
GUAVA="$PRISM/libraries/com/google/guava/guava/21.0/guava-21.0.jar"
STUB="$HERE/build/mc-stub.jar"
FORGE_STUB="$HERE/build/forge-stub.jar"   # Forge refers to MC by obfuscated names; rename those too

install_client=0
install_server=0
for arg in "$@"; do
  case "$arg" in
    --install-client) install_client=1 ;;
    --install-server) install_server=1 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

mkdir -p "$HERE/build"

# The stub is derived from files already on disk; rebuild only if missing.
if [[ ! -f "$STUB" ]]; then
  echo "building compile stub (one-off)"
  [[ -f "$HERE/build/joined.srg" ]] || python3 - "$HERE" "$FORGE" <<'PY'
import lzma, sys, zipfile, os
here, forge = sys.argv[1], sys.argv[2]
data = zipfile.ZipFile(forge).read("deobfuscation_data-1.12.2.lzma")
out = lzma.LZMADecompressor(format=lzma.FORMAT_ALONE).decompress(data)
open(os.path.join(here, "build", "joined.srg"), "wb").write(out)
PY
  mkdir -p "$HERE/build/tools"
  "$JAVA_HOME/bin/javac" -nowarn -cp "$ASM" -d "$HERE/build/tools" "$HERE/tools/StubGen.java"
  "$JAVA_HOME/bin/java" -cp "$ASM:$HERE/build/tools" StubGen "$CLIENT" "$HERE/build/joined.srg" "$STUB"
  "$JAVA_HOME/bin/java" -cp "$ASM:$HERE/build/tools" StubGen "$FORGE" "$HERE/build/joined.srg" "$FORGE_STUB"
fi

echo "compiling client half"
rm -rf "$HERE/build/classes" && mkdir -p "$HERE/build/classes"
"$JAVA_HOME/bin/javac" -nowarn -encoding UTF-8 -source 8 -target 8 -cp "$STUB:$FORGE_STUB:$NETTY:$LWJGL:$AUTHLIB:$GUAVA" \
  -d "$HERE/build/classes" "$HERE"/src/friendfinder/*.java "$HERE"/src/friendfinder/net/*.java
cp "$HERE/src/mcmod.info" "$HERE/build/classes/"
rm -f "$HERE/build/friendfinder.jar"
(cd "$HERE/build/classes" && "$JAVA_HOME/bin/jar" cf "$HERE/build/friendfinder.jar" .)

echo "compiling server half"
rm -rf "$HERE/build/classes-server" && mkdir -p "$HERE/build/classes-server"
"$JAVA_HOME/bin/javac" -nowarn -encoding UTF-8 -source 8 -target 8 -cp "$STUB:$FORGE_STUB:$NETTY:$LWJGL:$AUTHLIB:$GUAVA" \
  -d "$HERE/build/classes-server" "$HERE"/srcserver/friendfinder/server/*.java "$HERE"/src/friendfinder/net/*.java
cp "$HERE/srcserver/mcmod.info" "$HERE/build/classes-server/"
rm -f "$HERE/build/friendfinder-server.jar"
(cd "$HERE/build/classes-server" && "$JAVA_HOME/bin/jar" cf "$HERE/build/friendfinder-server.jar" .)

echo "built:"
echo "  $HERE/build/friendfinder.jar"
echo "  $HERE/build/friendfinder-server.jar"

if (( install_client )); then
  cp "$HERE/build/friendfinder.jar" "$INSTANCE/mods/friendfinder.jar"
  echo "installed client -> $INSTANCE/mods/friendfinder.jar  (restart the client)"
fi

if (( install_server )); then
  cp "$HERE/build/friendfinder-server.jar" "$SERVER/mods/friendfinder-server.jar"
  echo "installed server -> $SERVER/mods/friendfinder-server.jar  (inert until YOU restart the server)"
fi
