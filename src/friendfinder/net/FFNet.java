package friendfinder.net;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * The client and server halves talk over this channel.
 *
 * Both jars register the same two messages under the same discriminators, so the
 * wire format is shared, but each half only ever does something useful with one of
 * them. Nothing here touches client-only or server-only Minecraft classes.
 */
public final class FFNet {
    private FFNet() {}

    public static final String CHANNEL_NAME = "friendfinder";

    /**
     * Handshake version. 1: positions. 2: added health. 3: added mutual opt-in, which
     * is a rule change rather than a format change — a server will not share anything
     * with a client below 3, because such a client has no way to express consent.
     */
    public static final int PROTOCOL = 3;
    public static final int PROTOCOL_CONSENT = 3;

    /** Server sends positions this often. 4 Hz: smooth once interpolated, ~1 KB/s for a full server. */
    public static final int INTERVAL_TICKS = 5;
    public static final long INTERVAL_MS = 250L;

    /** Clients re-announce themselves this often, so a server restart re-subscribes them. */
    public static final int HELLO_TICKS = 100;

    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);

    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(PositionPacket.Handler.class, PositionPacket.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(HelloPacket.Handler.class, HelloPacket.class, 1, Side.SERVER);
        CHANNEL.registerMessage(SelectionPacket.Handler.class, SelectionPacket.class, 2, Side.SERVER);
        CHANNEL.registerMessage(RosterPacket.Handler.class, RosterPacket.class, 3, Side.CLIENT);
    }
}
