package friendfinder.net;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Players who announced they have the client half. Server side; written from the network thread. */
public final class Subscribers {
    private Subscribers() {}

    private static final Set<UUID> IDS = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    public static void add(UUID id) { IDS.add(id); }
    public static void remove(UUID id) { IDS.remove(id); }
    public static boolean contains(UUID id) { return IDS.contains(id); }
    public static boolean isEmpty() { return IDS.isEmpty(); }
}
