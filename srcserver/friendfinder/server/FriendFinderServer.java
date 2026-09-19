package friendfinder.server;

import friendfinder.net.FFNet;
import friendfinder.net.PositionPacket;
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

import java.util.List;
import java.util.UUID;

/**
 * Friend Finder, server half: tells each player where the others are, so their
 * markers work at any distance instead of stopping at view distance.
 *
 * Server-side only, and it only answers clients that announced the mod — so players
 * without it are never sent anything, and a client without it can still join.
 * Positions are filtered to the recipient's own dimension, so someone in the Nether
 * is simply absent rather than shown at a misleading overworld position.
 */
@Mod(modid = FriendFinderServer.MODID,
     name = "Friend Finder (server)",
     version = "1.2.0",
     serverSideOnly = true,
     acceptableRemoteVersions = "*",
     acceptedMinecraftVersions = "[1.12.2]")
public class FriendFinderServer {

    public static final String MODID = "friendfinderserver";

    private int ticks;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        FFNet.register();
        MinecraftForge.EVENT_BUS.register(this);   // 1.12 routes tick and player events here too
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++ticks % FFNet.INTERVAL_TICKS != 0) return;
        if (Subscribers.isEmpty()) return;

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;

        List<EntityPlayerMP> all = McServer.players(server);
        if (all.size() < 2) {
            // One player left online: still send, so a marker for someone who just
            // logged out clears immediately rather than lingering.
            for (EntityPlayerMP only : all) sendTo(only, all);
            return;
        }
        for (EntityPlayerMP to : all) sendTo(to, all);
    }

    private void sendTo(EntityPlayerMP to, List<EntityPlayerMP> all) {
        int protocol = Subscribers.protocolOf(McServer.uuid(to));
        if (protocol == 0) return;                               // never announced itself
        int dim = McServer.dimension(to);
        // Written in the format this particular client asked for, so an old client is
        // never handed bytes it cannot parse.
        PositionPacket packet = new PositionPacket(protocol);
        for (EntityPlayerMP other : all) {
            if (other == to) continue;
            if (McServer.dimension(other) != dim) continue;      // another dimension: hidden
            packet.add(McServer.uuid(other), McServer.posX(other), McServer.posY(other), McServer.posZ(other),
                       McServer.health(other));
        }
        try {
            // Sent even when empty: a full snapshot every time is what lets the client
            // drop markers the instant someone leaves or changes dimension.
            FFNet.CHANNEL.sendTo(packet, to);
        } catch (Throwable ignored) {
            // A player mid-disconnect must never interrupt the tick loop.
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = McServer.uuid(event.player);
        if (id != null) Subscribers.remove(id);
    }
}
