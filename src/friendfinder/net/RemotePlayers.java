package friendfinder.net;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Positions of players too far away to be real entities on this client.
 *
 * Updates land at 4 Hz on the network thread; the HUD reads this every frame. Each
 * entry glides from where it was to where the last packet said, over one update
 * interval, so a distant marker moves smoothly instead of stepping four times a second.
 * The cost is being a quarter-second behind, which is invisible at these distances.
 */
public final class RemotePlayers {
    private RemotePlayers() {}

    /** If the packets stop (server without the mod, or a dropped link), forget everything. */
    private static final long STALE_MS = 3000L;

    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<UUID, Entry>();
    private static volatile long lastPacket;

    public static final class Entry {
        public final UUID id;
        private volatile double px, py, pz;     // where it was when the last packet arrived
        private volatile double tx, ty, tz;     // where that packet said to go
        private volatile long tPrev, tEnd;

        Entry(UUID id, double x, double y, double z, long now) {
            this.id = id;
            this.px = this.tx = x;
            this.py = this.ty = y;
            this.pz = this.tz = z;
            this.tPrev = now;
            this.tEnd = now;
        }

        void retarget(double x, double y, double z, long now) {
            double[] at = at(now);
            px = at[0]; py = at[1]; pz = at[2];
            tx = x; ty = y; tz = z;
            tPrev = now;
            tEnd = now + FFNet.INTERVAL_MS;
        }

        /** Interpolated position at `now`. */
        public double[] at(long now) {
            long span = tEnd - tPrev;
            double a = span <= 0 ? 1.0 : (double) (now - tPrev) / (double) span;
            if (a < 0.0) a = 0.0;
            if (a > 1.0) a = 1.0;
            return new double[]{px + (tx - px) * a, py + (ty - py) * a, pz + (tz - pz) * a};
        }
    }

    public static void accept(List<PositionPacket.Pos> snapshot) {
        long now = System.currentTimeMillis();
        lastPacket = now;
        Set<UUID> seen = new HashSet<UUID>();
        for (PositionPacket.Pos p : snapshot) {
            seen.add(p.id);
            Entry e = ENTRIES.get(p.id);
            if (e == null) {
                ENTRIES.put(p.id, new Entry(p.id, p.x, p.y, p.z, now));
            } else {
                e.retarget(p.x, p.y, p.z, now);
            }
        }
        // Every packet is a complete snapshot, so anyone missing from it has left,
        // disconnected, or changed dimension. Drop them immediately.
        ENTRIES.keySet().retainAll(seen);
    }

    public static Collection<Entry> current() {
        if (ENTRIES.isEmpty()) return new ArrayList<Entry>();
        if (System.currentTimeMillis() - lastPacket > STALE_MS) {
            ENTRIES.clear();
            return new ArrayList<Entry>();
        }
        return new ArrayList<Entry>(ENTRIES.values());
    }

    public static void clear() {
        ENTRIES.clear();
    }
}
