# Friend Finder

A client-side Minecraft 1.12.2 mod: a floating head for every other player on the
server, so you can find each other without asking for coordinates.

- **In view** — the head is drawn over their actual position.
- **Off screen or behind you** — pinned to the edge of the screen with an arrow
  pointing the way you would need to turn.
- **Underneath** — the exact distance in whole metres, white with a black outline.
- **Above** — a thin health bar, one face-pixel tall, green on black. Full at ten
  hearts or more.
- **Hidden only** when a player is within ten metres, already on your screen, *and*
  actually visible — a single ray trace to their head decides the last part, so
  someone standing behind a wall still gets a marker. Behind you or further off, it
  always shows. F1 hides everything with the rest of the HUD.

No configuration, no keybinds, no GUI.

## Two halves

**Client half** (`friendfinder.jar`, client-side only) works on its own against any
server, but only as far as the server tells your client about other players — inside
your view distance and your own dimension. That is about **140 blocks** on the family
server (`view-distance=10`). There is no client-side trick around it: past that range
the data is not sent at all. JourneyMap's player radar has the same limit for the
same reason.

**Server half** (`friendfinder-server.jar`, server-side only) lifts the range
entirely. Four times a second it sends each player the positions of everyone else
**in the same dimension**. Someone in the Nether is simply absent from the snapshot,
so their marker disappears rather than pointing at a misleading overworld position.

They are deliberately two jars rather than one common mod. Side-locked, neither can
ever be required by the other: the server still accepts clients without it, your
client still joins servers without it, and family members who never update are
unaffected. Anyone running the old client half just keeps short-range markers.

The server only sends to clients that announced themselves (a `HelloPacket` repeated
every five seconds, so a server restart re-subscribes everyone without reconnecting).
Players without the mod are never sent anything.

### Mixed versions are safe

The `HelloPacket` carries the protocol the client speaks, and the server replies in
that format — so an old client is never handed bytes it would misparse. A new client
reads either format, telling them apart by a marker byte the old format cannot
produce. In practice:

| | old server | new server |
|---|---|---|
| **old client** | works | works, no health over long range |
| **new client** | works, no health over long range | everything |

Health for players close enough to be real entities always works: it is synced to
every client tracking them, so it needs nothing from the server half at all.

Skins work at any distance: the tab list already carries every online player's skin,
so a marker 5000 blocks away still shows the right face.

## Building

```
./build.sh                    # build both jars into build/, install nothing
./build.sh --install-client   # also copy into the Prism instance
./build.sh --install-server   # also copy into the dedicated server
```

The client goes to the **Prism Launcher** instance only — not the CurseForge copy.
Restart the client to pick up a new build.

Installing the server jar only puts a file on disk; mods load at startup, so it does
nothing until the server is restarted. **The build script never starts, stops or
signals the server** — restarting is always a deliberate, manual act.

### How it builds without Gradle

A Forge mod links against Minecraft classes under MCP class names with SRG member
names (`func_71410_x`), which is what FML remaps the obfuscated client to at
runtime. ForgeGradle normally gives you readable names and reobfuscates on the way
out; that means a large download and a slow decompile.

Instead `tools/StubGen.java` builds a **compile-only stub**: it reads the
obfuscated client jar plus `deobfuscation_data-1.12.2.lzma` (already inside the
Forge jar), renames classes and members, and drops every method body — javac only
needs signatures. Compiling against that stub produces a jar that is already in
runtime names, so there is no reobfuscation step at all.

The Forge jar itself gets the same treatment (`forge-stub.jar`). Forge's shipped
classes refer to Minecraft by *obfuscated* names — `sendTo(IMessage, oq)` — because
FML remaps them as it loads them. Compiling against the raw Forge jar therefore
fails: its `oq` and our `EntityPlayerMP` are different types to javac. Renaming both
jars with the same mapping makes them agree, and the emitted call site then matches
Forge's real runtime signature exactly.

`src/friendfinder/Mc.java` keeps every one of those `func_`/`field_` calls in one
file with the readable name in its javadoc, so the rest of the mod reads normally.

## Known limits

- Third-person view (F5) projects from your eyes, not the offset camera, so
  markers are slightly off in third person at close range.
- Glass counts as solid to the ray trace, so a player visible through a window is
  treated as hidden and keeps their marker. That errs toward showing too much, which
  is the safe direction.
- Invisible players are still shown.
- Skins come from the normal skin system; a player whose skin has not downloaded
  yet shows the default one.
