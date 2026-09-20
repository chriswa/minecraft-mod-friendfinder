package friendfinder.server;

import friendfinder.net.FFNet;
import friendfinder.net.PositionPacket;
import friendfinder.net.RosterPacket;
import friendfinder.net.Selections;
import friendfinder.net.Subscribers;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Friend Finder, server half: tells each player where the others are, so their
 * markers work at any distance instead of stopping at view distance.
 *
 * Nothing is shared unless both people asked for it. A position crosses between two
 * players only when each has picked the other, and it is filtered to the recipient's
 * own dimension, so someone in the Nether is simply absent rather than shown at a
 * misleading overworld position.
 *
 * Server-side only. Clients without the mod are never sent anything and can still
 * join normally.
 */
@Mod(modid = FriendFinderServer.MODID,
     name = "Friend Finder (server)",
     version = "1.4.1",
     serverSideOnly = true,
     acceptableRemoteVersions = "*",
     acceptedMinecraftVersions = "[1.12.2]")
public class FriendFinderServer {

    public static final String MODID = "friendfinderserver";

    private int ticks;
    private final Set<UUID> warned = new HashSet<UUID>();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        FFNet.register();
        MinecraftForge.EVENT_BUS.register(this);   // 1.12 routes tick and player events here too
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        List<EntityPlayerMP> all = McServer.players(server);
        if (all.isEmpty()) return;

        // Somebody changed their picks, joined, or left: refresh who-picked-whom.
        if (Selections.consumeDirty()) broadcastRoster(all);

        if (++ticks % FFNet.INTERVAL_TICKS != 0) return;
        if (Subscribers.isEmpty()) return;
        // Everyone gets a snapshot, even the last player online, so a marker for
        // someone who just logged out clears at once rather than lingering.
        for (EntityPlayerMP to : all) sendTo(to, all);
        nagOutdated(all);
    }

    private void sendTo(EntityPlayerMP to, List<EntityPlayerMP> all) {
        UUID id = McServer.uuid(to);
        int protocol = Subscribers.protocolOf(id);
        if (protocol == 0) return;                               // never announced itself
        if (protocol < FFNet.PROTOCOL_CONSENT) return;           // too old to express consent
        int dim = McServer.dimension(to);
        PositionPacket packet = new PositionPacket(protocol);
        for (EntityPlayerMP other : all) {
            if (other == to) continue;
            if (McServer.dimension(other) != dim) continue;              // another dimension: hidden
            if (!Selections.mutual(id, McServer.uuid(other))) continue;  // not mutually agreed
            packet.add(McServer.uuid(other), McServer.posX(other), McServer.posY(other), McServer.posZ(other),
                       McServer.health(other));
        }
        try {
            // Sent even when empty: a full snapshot every time is what lets the client
            // drop markers the instant someone leaves, changes dimension, or unticks you.
            FFNet.CHANNEL.sendTo(packet, to);
        } catch (Throwable ignored) {
            // A player mid-disconnect must never interrupt the tick loop.
        }
    }

    /** Tell each player which of the people currently online have picked them. */
    private void broadcastRoster(List<EntityPlayerMP> all) {
        List<UUID> online = new ArrayList<UUID>(all.size());
        for (EntityPlayerMP p : all) online.add(McServer.uuid(p));
        for (EntityPlayerMP to : all) {
            UUID id = McServer.uuid(to);
            if (Subscribers.protocolOf(id) < FFNet.PROTOCOL_CONSENT) continue;
            try {
                FFNet.CHANNEL.sendTo(new RosterPacket(Selections.whoPicked(id, online)), to);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * A client older than the consent protocol would otherwise see nothing at all and
     * have no idea why, so say it once, in chat.
     */
    private void nagOutdated(List<EntityPlayerMP> all) {
        for (EntityPlayerMP p : all) {
            UUID id = McServer.uuid(p);
            int protocol = Subscribers.protocolOf(id);
            if (protocol == 0 || protocol >= FFNet.PROTOCOL_CONSENT) continue;
            if (!warned.add(id)) continue;
            McServer.tell(p, "§e[Friend Finder] §fYour version is out of date, so nothing is being shared. "
                    + "Update the mod, then use §a/friendfinder§f to pick who you share with.");
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Selections.markDirty();     // the arrival needs a roster, and the others need theirs
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = McServer.uuid(event.player);
        if (id != null) {
            Subscribers.remove(id);
            Selections.forget(id);   // their picks leave with them; their client re-sends on return
            warned.remove(id);
        }
    }
}
