package friendfinder.net;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who each player is willing to share with. Server side, memory only.
 *
 * Nothing is persisted here on purpose: every client re-sends its full set on join and
 * repeatedly afterwards, so the clients stay the source of truth and a server restart
 * costs nothing. It also means the server never holds a list of anyone's friends any
 * longer than they are connected.
 */
public final class Selections {
    private Selections() {}

    private static final Map<UUID, Set<UUID>> PICKS = new ConcurrentHashMap<UUID, Set<UUID>>();
    private static volatile boolean dirty;

    public static void set(UUID who, Set<UUID> picks) {
        Set<UUID> now = new HashSet<UUID>(picks);
        Set<UUID> before = PICKS.put(who, now);
        if (before == null || !before.equals(now)) dirty = true;
    }

    public static void forget(UUID who) {
        if (PICKS.remove(who) != null) dirty = true;
    }

    /** Sharing needs both sides: neither position moves until each has picked the other. */
    public static boolean mutual(UUID a, UUID b) {
        Set<UUID> pa = PICKS.get(a);
        if (pa == null || !pa.contains(b)) return false;
        Set<UUID> pb = PICKS.get(b);
        return pb != null && pb.contains(a);
    }

    /** Of the given online players, those who have picked {@code me}. */
    public static Set<UUID> whoPicked(UUID me, Iterable<UUID> online) {
        Set<UUID> out = new HashSet<UUID>();
        for (UUID other : online) {
            if (other.equals(me)) continue;
            Set<UUID> theirs = PICKS.get(other);
            if (theirs != null && theirs.contains(me)) out.add(other);
        }
        return out;
    }

    /** True once since the last call: used to push rosters only when something changed. */
    public static boolean consumeDirty() {
        if (!dirty) return false;
        dirty = false;
        return true;
    }

    public static void markDirty() { dirty = true; }

    public static Set<UUID> known() { return Collections.unmodifiableSet(PICKS.keySet()); }
}
