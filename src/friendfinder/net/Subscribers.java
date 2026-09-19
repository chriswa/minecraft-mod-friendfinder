package friendfinder.net;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Players who announced they have the client half, and which protocol they speak.
 * Server side; written from the network thread.
 */
public final class Subscribers {
    private Subscribers() {}

    private static final Map<UUID, Integer> VERSIONS = new ConcurrentHashMap<UUID, Integer>();

    public static void add(UUID id, int protocol) { VERSIONS.put(id, protocol); }
    public static void remove(UUID id) { VERSIONS.remove(id); }
    public static boolean isEmpty() { return VERSIONS.isEmpty(); }

    /** 0 when they have not announced themselves; otherwise the protocol they asked for. */
    public static int protocolOf(UUID id) {
        Integer v = VERSIONS.get(id);
        return v == null ? 0 : v;
    }
}
