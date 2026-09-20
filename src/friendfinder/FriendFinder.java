package friendfinder;

import friendfinder.net.FFNet;
import friendfinder.net.Friends;
import friendfinder.net.HelloPacket;
import friendfinder.net.RemotePlayers;
import friendfinder.net.SelectionPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Friend Finder — a marker showing where the players you share with are.
 *
 * Sharing is mutual and opt-in: run {@code /friendfinder} (or {@code /ff}) and tick
 * someone; nothing passes between you until they tick you back.
 *
 * The client half works on its own against any server, limited to the players the
 * server already sends it — roughly view-distance * 16 blocks. Install the server
 * half as well and markers work at any distance in the same dimension.
 */
@Mod(modid = FriendFinder.MODID,
     name = "Friend Finder",
     version = "1.4.0",
     clientSideOnly = true,
     acceptedMinecraftVersions = "[1.12.2]")
public class FriendFinder {

    public static final String MODID = "friendfinder";

    private int ticks;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Friends.load(event.getModConfigurationDirectory());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        FFNet.register();
        ClientCommandHandler.instance.func_71560_a(new CommandFriends());   // registerCommand
        MinecraftForge.EVENT_BUS.register(new Hud());
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Announce ourselves and re-send our picks. Repeated rather than sent once, so a
     * server that restarts (or has the mod added later) rebuilds both without anyone
     * having to reconnect.
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
                sendSelection();
            } catch (Throwable ignored) {
                // Server without the mod, or mid-disconnect: markers just stay short-range.
            }
        }
    }

    /** Push the current picks now, so ticking a box takes effect immediately. */
    public static void sendSelection() {
        Minecraft mc = Mc.mc();
        if (mc == null || Mc.self(mc) == null || Mc.connection(mc) == null) return;
        try {
            FFNet.CHANNEL.sendToServer(new SelectionPacket(Mc.uuid(Mc.self(mc)), Friends.mine()));
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        RemotePlayers.clear();
        Friends.onDisconnect();
        ticks = 0;
    }
}
