package friendfinder.server;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;

/**
 * Every Minecraft call the server half makes, in one place.
 *
 * Deliberately separate from the client's Mc facade: nothing here may touch a
 * client-only class, or a dedicated server would fail to load the mod.
 */
final class McServer {
    private McServer() {}

    /** server.getPlayerList().getPlayers() */
    static List<EntityPlayerMP> players(MinecraftServer server) {
        return server.func_184103_al().func_181057_v();
    }

    /** entity.dimension */
    static int dimension(EntityPlayer p) { return p.field_71093_bK; }

    /** entity.getUniqueID() */
    static UUID uuid(EntityPlayer p) { return p.func_110124_au(); }

    /** entity.getHealth() */
    static float health(EntityPlayer p) { return p.func_110143_aJ(); }

    /** entity.posX / posY / posZ */
    static double posX(EntityPlayer p) { return p.field_70165_t; }
    static double posY(EntityPlayer p) { return p.field_70163_u; }
    static double posZ(EntityPlayer p) { return p.field_70161_v; }
}
