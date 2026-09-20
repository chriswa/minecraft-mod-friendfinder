package friendfinder.net;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who you have chosen to share with, and who has chosen you. Client side.
 *
 * Sharing is mutual: a position only moves between two people who have both ticked
 * each other. Your own choices are the source of truth and live in a file next to
 * the config; the server keeps them only for as long as you are connected, and your
 * client re-sends them on join and every few seconds after.
 *
 * Deliberately free of Minecraft classes so the shared packet handlers can touch it.
 */
public final class Friends {
    private Friends() {}

    /** Your picks: the people you are willing to share with. Persisted. */
    private static final Set<UUID> MINE = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    /** People online who have picked you. Pushed by the server, never persisted. */
    private static final Set<UUID> THEIRS = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    /** Last known names, so the saved file is readable by a human. */
    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<UUID, String>();

    /** True once the server has told us anything, i.e. it is running the server half. */
    private static volatile boolean serverAware;
    private static volatile File file;

    public static boolean isMine(UUID id) { return MINE.contains(id); }
    public static boolean hasPickedMe(UUID id) { return THEIRS.contains(id); }
    public static boolean isMutual(UUID id) { return MINE.contains(id) && THEIRS.contains(id); }
    public static boolean serverAware() { return serverAware; }
    public static Set<UUID> mine() { return MINE; }

    public static void remember(UUID id, String name) {
        if (id != null && name != null) NAMES.put(id, name);
    }

    public static void toggle(UUID id, String name) {
        remember(id, name);
        if (!MINE.remove(id)) MINE.add(id);
        save();
    }

    /** Replaces the set of people who have picked you; called from the network thread. */
    public static void setPickedMeBy(Set<UUID> ids) {
        serverAware = true;
        THEIRS.retainAll(ids);
        THEIRS.addAll(ids);
    }

    /** Forget the server's half of the picture, but never your own picks. */
    public static void onDisconnect() {
        THEIRS.clear();
        serverAware = false;
    }

    // ------------------------------------------------------------------ storage

    public static void load(File configDir) {
        file = new File(configDir, "friendfinder-friends.txt");
        if (!file.isFile()) return;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(file));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int sp = line.indexOf(' ');
                String idPart = sp < 0 ? line : line.substring(0, sp);
                try {
                    UUID id = UUID.fromString(idPart);
                    MINE.add(id);
                    if (sp > 0) NAMES.put(id, line.substring(sp + 1).trim());
                } catch (IllegalArgumentException ignored) {
                    // A hand-edited line we cannot parse should not lose the rest of the file.
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) {}
        }
    }

    public static void save() {
        File f = file;
        if (f == null) return;
        PrintWriter w = null;
        try {
            Map<UUID, String> ordered = new LinkedHashMap<UUID, String>();
            for (UUID id : MINE) ordered.put(id, NAMES.get(id));
            w = new PrintWriter(f, "UTF-8");
            w.println("# Friend Finder: people you are willing to share your position with.");
            w.println("# Both of you must pick each other before anything is shared.");
            for (Map.Entry<UUID, String> e : ordered.entrySet()) {
                w.println(e.getValue() == null ? e.getKey().toString() : e.getKey() + " " + e.getValue());
            }
        } catch (Exception ignored) {
        } finally {
            if (w != null) w.close();
        }
    }
}
