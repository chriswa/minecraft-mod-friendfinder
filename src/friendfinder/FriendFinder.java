package friendfinder;

import friendfinder.net.FFNet;
import friendfinder.net.HelloPacket;
import friendfinder.net.RemotePlayers;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Friend Finder — a marker showing where the other players are.
 *
 * The client half works on its own against any server: it uses the players the
 * server already sends us, which reaches about view-distance * 16 blocks. Install
 * the server half as well and markers work at any distance in the same dimension.
 */
@Mod(modid = FriendFinder.MODID,
     name = "Friend Finder",
     version = "1.1.0",
     clientSideOnly = true,
     acceptedMinecraftVersions = "[1.12.2]")
public class FriendFinder {

    public static final String MODID = "friendfinder";

    private int ticks;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        FFNet.register();
        MinecraftForge.EVENT_BUS.register(new Hud());
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Tell the server we can use position updates. Repeated rather than sent once, so
     * that a server which restarts (or has the mod added later) picks us back up
     * without us having to reconnect.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Mc.mc();
        if (mc == null || Mc.self(mc) == null || Mc.connection(mc) == null) {
            ticks = 0;
            return;
        }
        if (ticks-- <= 0) {
            ticks = FFNet.HELLO_TICKS;
            try {
                FFNet.CHANNEL.sendToServer(new HelloPacket(Mc.uuid(Mc.self(mc))));
            } catch (Throwable ignored) {
                // Server without the mod, or mid-disconnect: markers just stay short-range.
            }
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        RemotePlayers.clear();
        ticks = 0;
    }
}
