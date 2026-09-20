# Friend Finder

## The two halves share one version number

The client and server ship as separate jars, but their versions are a single number.
Bump both together — **including the half that did not change** — and never publish a
pair whose numbers differ. The release tag uses the same number.

Someone holding two jars has no other way to tell whether they belong together. Let
the numbers drift and compatibility becomes something people have to look up, or
guess.

Four places to change, plus the tag:

- `src/friendfinder/FriendFinder.java` — `@Mod(version = ...)`
- `src/mcmod.info` — `"version"`
- `srcserver/friendfinder/server/FriendFinderServer.java` — `@Mod(version = ...)`
- `srcserver/mcmod.info` — `"version"`

Actual wire compatibility is a separate thing, negotiated at runtime by
`FFNet.PROTOCOL`; version numbers are for humans deciding which files to install.
