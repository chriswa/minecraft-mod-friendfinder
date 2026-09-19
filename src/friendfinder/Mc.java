package friendfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Every Minecraft call this mod makes, in one place.
 *
 * We compile against a stub of the client jar carrying the names a mod sees at
 * runtime (SRG: func_xxx / field_xxx), so the rest of the mod can read normally.
 * The MCP name of each member is in its javadoc.
 */
final class Mc {
    private Mc() {}

    /** Minecraft.getMinecraft() */
    static Minecraft mc() { return Minecraft.func_71410_x(); }

    /** mc.world */
    static net.minecraft.world.World world(Minecraft mc) { return mc.field_71441_e; }

    /** mc.player */
    static EntityPlayer self(Minecraft mc) { return mc.field_71439_g; }

    /** mc.getRenderViewEntity() */
    static Entity viewEntity(Minecraft mc) { return mc.func_175606_aa(); }

    /** world.playerEntities */
    static List<EntityPlayer> players(net.minecraft.world.World w) { return w.field_73010_i; }

    /** mc.gameSettings.hideGUI */
    static boolean hideGui(Minecraft mc) { return mc.field_71474_y.field_74319_N; }

    /** mc.gameSettings.thirdPersonView (0 = first person, 2 = front-facing) */
    static int thirdPersonView(Minecraft mc) { return mc.field_71474_y.field_74320_O; }

    /** mc.gameSettings.fovSetting */
    static float fovSetting(Minecraft mc) { return mc.field_71474_y.field_74334_X; }

    /** mc.displayWidth / mc.displayHeight */
    static int displayWidth(Minecraft mc) { return mc.field_71443_c; }
    static int displayHeight(Minecraft mc) { return mc.field_71440_d; }

    /** entity.posX / posY / posZ */
    static double posX(Entity e) { return e.field_70165_t; }
    static double posY(Entity e) { return e.field_70163_u; }
    static double posZ(Entity e) { return e.field_70161_v; }

    /** entity.lastTickPosX / lastTickPosY / lastTickPosZ — where rendering interpolates from */
    static double lastX(Entity e) { return e.field_70142_S; }
    static double lastY(Entity e) { return e.field_70137_T; }
    static double lastZ(Entity e) { return e.field_70136_U; }

    /** entity.rotationYaw / rotationPitch, and their prev-tick values */
    static float yaw(Entity e) { return e.field_70177_z; }
    static float pitch(Entity e) { return e.field_70125_A; }
    static float prevYaw(Entity e) { return e.field_70126_B; }
    static float prevPitch(Entity e) { return e.field_70127_C; }

    /** entity.getEyeHeight() */
    static float eyeHeight(Entity e) { return e.func_70047_e(); }

    /** entity.height */
    static float height(Entity e) { return e.field_70131_O; }

    /** entity.getName() */
    static String name(Entity e) { return e.func_70005_c_(); }

    /** player.getLocationSkin() — the downloaded skin, or the default one */
    static ResourceLocation skin(AbstractClientPlayer p) { return p.func_110306_p(); }

    /** entity.getUniqueID() */
    static UUID uuid(Entity e) { return e.func_110124_au(); }

    /**
     * player.getHealth() — synced to every client tracking the entity, so this is the
     * other player's real health, not a guess.
     */
    static float health(EntityPlayer p) { return p.func_110143_aJ(); }

    /** Gui.drawRect(left, top, right, bottom, argb) */
    static void rect(int left, int top, int right, int bottom, int argb) {
        Gui.func_73734_a(left, top, right, bottom, argb);
    }

    /**
     * True when nothing solid stands between the two points — world.rayTraceBlocks().
     * It traces against collision boxes, so glass counts as solid: someone visible
     * through a window is treated as hidden, which errs toward showing their marker.
     */
    static boolean lineOfSight(World w, double x1, double y1, double z1, double x2, double y2, double z2) {
        try {
            return w.func_72933_a(new Vec3d(x1, y1, z1), new Vec3d(x2, y2, z2)) == null;
        } catch (Throwable t) {
            return true;    // never let a trace failure hide someone's marker forever
        }
    }

    /** mc.getConnection() */
    static NetHandlerPlayClient connection(Minecraft mc) { return mc.func_147114_u(); }

    /**
     * The skin of a player we cannot see, by UUID. The tab list carries every online
     * player's skin regardless of distance, which is what lets a marker 5000 blocks
     * away still show the right face.
     */
    static ResourceLocation skinFor(Minecraft mc, UUID id) {
        NetHandlerPlayClient c = connection(mc);
        if (c != null) {
            NetworkPlayerInfo info = c.func_175102_a(id);            // getPlayerInfo(uuid)
            if (info != null) return info.func_178837_g();           // getLocationSkin()
        }
        return DefaultPlayerSkin.func_177334_a(id);                  // getDefaultSkin(uuid)
    }

    /** mc.getTextureManager().bindTexture(loc) */
    static void bindTexture(Minecraft mc, ResourceLocation loc) { mc.func_110434_K().func_110577_a(loc); }

    /** new ScaledResolution(mc) — GUI-space size */
    static int scaledWidth(Minecraft mc) { return new ScaledResolution(mc).func_78326_a(); }
    static int scaledHeight(Minecraft mc) { return new ScaledResolution(mc).func_78328_b(); }

    /** fontRenderer.drawString(text, x, y, colour) */
    static void drawString(Minecraft mc, String s, int x, int y, int colour) {
        mc.field_71466_p.func_78276_b(s, x, y, colour);
    }

    /** fontRenderer.getStringWidth(text) */
    static int stringWidth(Minecraft mc, String s) { return mc.field_71466_p.func_78256_a(s); }

    /** Gui.drawScaledCustomSizeModalRect(...) — blit a region of a texture */
    static void blit(int x, int y, float u, float v, int uW, int vH, int w, int h, float texW, float texH) {
        Gui.func_152125_a(x, y, u, v, uW, vH, w, h, texW, texH);
    }

    // GlStateManager
    static void pushMatrix() { GlStateManager.func_179094_E(); }
    static void popMatrix() { GlStateManager.func_179121_F(); }
    static void translate(float x, float y, float z) { GlStateManager.func_179109_b(x, y, z); }
    static void rotate(float deg, float x, float y, float z) { GlStateManager.func_179114_b(deg, x, y, z); }
    static void scale(float x, float y, float z) { GlStateManager.func_179152_a(x, y, z); }
    static void enableBlend() { GlStateManager.func_179147_l(); }
    static void disableBlend() { GlStateManager.func_179084_k(); }
    static void blendFunc() { GlStateManager.func_179120_a(770, 771, 1, 0); }
    static void colour(float r, float g, float b, float a) { GlStateManager.func_179131_c(r, g, b, a); }
    static void enableTexture() { GlStateManager.func_179098_w(); }
    static void disableTexture() { GlStateManager.func_179090_x(); }
    static void enableDepth() { GlStateManager.func_179126_j(); }
    static void disableDepth() { GlStateManager.func_179097_i(); }

    /** Draws a filled triangle in GUI space (no texture). */
    static void triangle(float x1, float y1, float x2, float y2, float x3, float y3, float r, float g, float b, float a) {
        Tessellator t = Tessellator.func_178181_a();
        BufferBuilder buf = t.func_178180_c();
        buf.func_181668_a(4 /* GL_TRIANGLES */, DefaultVertexFormats.field_181706_f);
        buf.func_181662_b(x1, y1, 0).func_181666_a(r, g, b, a).func_181675_d();
        buf.func_181662_b(x2, y2, 0).func_181666_a(r, g, b, a).func_181675_d();
        buf.func_181662_b(x3, y3, 0).func_181666_a(r, g, b, a).func_181675_d();
        t.func_78381_a();
    }

    /**
     * The vertical FOV the world was actually rendered with this frame, in degrees —
     * EntityRenderer.getFOVModifier(partialTicks, true). It is private, so we reflect;
     * without it, markers would drift while sprinting or under a potion effect.
     */
    private static Method fovModifier;
    private static boolean fovFailed;

    static float fov(Minecraft mc, float partialTicks) {
        if (!fovFailed) {
            try {
                if (fovModifier == null) {
                    fovModifier = EntityRenderer.class.getDeclaredMethod("func_78481_a", float.class, boolean.class);
                    fovModifier.setAccessible(true);
                }
                return (Float) fovModifier.invoke(mc.field_71460_t, partialTicks, true);
            } catch (Throwable t) {
                fovFailed = true;   // fall back to the raw setting for the rest of the session
            }
        }
        return fovSetting(mc);
    }
}
