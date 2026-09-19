package friendfinder;

import friendfinder.net.RemotePlayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Draws a floating head for every other player, always.
 *
 * In front of you: over their actual position. Off screen or behind you: pinned to
 * the edge with an arrow pointing the way you would need to turn. Under each head,
 * the exact distance in whole metres.
 *
 * Positions come from whichever source is better: a real loaded entity when the
 * player is close enough for the server to send one, otherwise the position feed
 * from the server-side half (if it is installed), which has no range limit.
 */
public class Hud {

    private static final int FACE = 16;          // drawn head size, GUI pixels
    private static final int MARGIN = 20;        // keep edge markers this far in
    private static final int MARGIN_BOTTOM = 42; // ... and clear of the hotbar
    private static final double HEAD_GAP = 0.35; // marker sits this far above the head
    private static final double REMOTE_HEIGHT = 1.8; // assumed height for players we cannot see

    /** A player to draw: feet position, where the marker floats, and their skin. */
    private static final class Target {
        final double x, y, z, markerY;
        final ResourceLocation skin;
        Target(double x, double y, double z, double markerY, ResourceLocation skin) {
            this.x = x; this.y = y; this.z = z; this.markerY = markerY; this.skin = skin;
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

        Minecraft mc = Mc.mc();
        if (mc == null || Mc.world(mc) == null || Mc.self(mc) == null) return;
        if (Mc.hideGui(mc)) return;

        EntityPlayer self = Mc.self(mc);
        float pt = event.getPartialTicks();

        List<Target> targets = collect(mc, self, pt);
        if (targets.isEmpty()) return;

        Entity view = Mc.viewEntity(mc);
        if (view == null) view = self;

        // Camera: eye position and facing, interpolated the way the world render does it.
        double camX = lerp(Mc.lastX(view), Mc.posX(view), pt);
        double camY = lerp(Mc.lastY(view), Mc.posY(view), pt) + Mc.eyeHeight(view);
        double camZ = lerp(Mc.lastZ(view), Mc.posZ(view), pt);
        float yaw = lerpAngle(Mc.prevYaw(view), Mc.yaw(view), pt);
        float pitch = lerpAngle(Mc.prevPitch(view), Mc.pitch(view), pt);
        if (Mc.thirdPersonView(mc) == 2) {      // front-facing third person looks back at you
            yaw += 180.0F;
            pitch = -pitch;
        }

        double yawR = Math.toRadians(yaw), pitchR = Math.toRadians(pitch);
        double cosP = Math.cos(pitchR);
        // Forward, and a right vector that stays level with the horizon (no roll).
        double fx = -Math.sin(yawR) * cosP, fy = -Math.sin(pitchR), fz = Math.cos(yawR) * cosP;
        double rx = -Math.cos(yawR), rz = -Math.sin(yawR);
        // up = right x forward, with right.y == 0: (-rz*fy, rz*fx - rx*fz, rx*fy)
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;

        int sw = Mc.scaledWidth(mc), sh = Mc.scaledHeight(mc);
        double tanHalf = Math.tan(Math.toRadians(Mc.fov(mc, pt)) / 2.0);
        double aspect = (double) Mc.displayWidth(mc) / Math.max(1, Mc.displayHeight(mc));

        double selfX = lerp(Mc.lastX(self), Mc.posX(self), pt);
        double selfY = lerp(Mc.lastY(self), Mc.posY(self), pt);
        double selfZ = lerp(Mc.lastZ(self), Mc.posZ(self), pt);

        Mc.pushMatrix();
        Mc.enableBlend();
        Mc.blendFunc();
        Mc.disableDepth();
        Mc.colour(1.0F, 1.0F, 1.0F, 1.0F);

        for (Target t : targets) {
            double ddx = t.x - selfX, ddy = t.y - selfY, ddz = t.z - selfZ;
            long metres = Math.round(Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz));

            double relX = t.x - camX, relY = t.markerY - camY, relZ = t.z - camZ;
            double camRight = relX * rx + relZ * rz;
            double camUp = relX * ux + relY * uy + relZ * uz;
            double camFwd = relX * fx + relY * fy + relZ * fz;

            double x = 0, y = 0;
            boolean onScreen = false;
            if (camFwd > 0.05) {
                double ndcX = (camRight / camFwd) / (tanHalf * aspect);
                double ndcY = (camUp / camFwd) / tanHalf;
                x = (ndcX * 0.5 + 0.5) * sw;
                y = (0.5 - ndcY * 0.5) * sh;
                onScreen = x >= MARGIN && x <= sw - MARGIN && y >= MARGIN && y <= sh - MARGIN_BOTTOM;
            }

            if (onScreen) {
                drawMarker(mc, t.skin, x, y, metres, 0.0, 0.0, false);
            } else {
                // Which way to turn: the projected direction when they are in front of
                // us, the raw camera-space offset when they are behind.
                double dx, dy;
                if (camFwd > 0.05) {
                    dx = x - sw / 2.0;
                    dy = y - sh / 2.0;
                } else {
                    dx = camRight;
                    dy = -camUp;
                }
                double len = Math.sqrt(dx * dx + dy * dy);
                if (len < 1e-6) { dx = 1; dy = 0; len = 1; }
                dx /= len;
                dy /= len;

                double cx = sw / 2.0, cy = sh / 2.0;
                double tx = dx > 0 ? (sw - MARGIN - cx) / dx : (dx < 0 ? (MARGIN - cx) / dx : Double.MAX_VALUE);
                double ty = dy > 0 ? (sh - MARGIN_BOTTOM - cy) / dy : (dy < 0 ? (MARGIN - cy) / dy : Double.MAX_VALUE);
                double d = Math.min(tx, ty);
                drawMarker(mc, t.skin, cx + dx * d, cy + dy * d, metres, dx, dy, true);
            }
        }

        Mc.colour(1.0F, 1.0F, 1.0F, 1.0F);
        Mc.enableDepth();
        Mc.disableBlend();
        Mc.popMatrix();
    }

    /** Loaded players first; anyone the server feed knows about but we cannot see, after. */
    private List<Target> collect(Minecraft mc, EntityPlayer self, float pt) {
        List<Target> out = new ArrayList<Target>();
        Set<UUID> loaded = new HashSet<UUID>();

        List<EntityPlayer> players = Mc.players(Mc.world(mc));
        if (players != null) {
            for (EntityPlayer p : players) {
                if (p == self || !(p instanceof AbstractClientPlayer)) continue;
                loaded.add(Mc.uuid(p));
                double x = lerp(Mc.lastX(p), Mc.posX(p), pt);
                double y = lerp(Mc.lastY(p), Mc.posY(p), pt);
                double z = lerp(Mc.lastZ(p), Mc.posZ(p), pt);
                out.add(new Target(x, y, z, y + Mc.height(p) + HEAD_GAP, Mc.skin((AbstractClientPlayer) p)));
            }
        }

        long now = System.currentTimeMillis();
        for (RemotePlayers.Entry e : RemotePlayers.current()) {
            if (loaded.contains(e.id)) continue;   // a real entity is exact and perfectly smooth
            double[] at = e.at(now);
            out.add(new Target(at[0], at[1], at[2], at[1] + REMOTE_HEIGHT + HEAD_GAP, Mc.skinFor(mc, e.id)));
        }
        return out;
    }

    private void drawMarker(Minecraft mc, ResourceLocation skin, double x, double y,
                            long metres, double dx, double dy, boolean edge) {
        int left = (int) Math.round(x) - FACE / 2;
        int top = (int) Math.round(y) - FACE / 2;

        Mc.enableTexture();
        Mc.colour(1.0F, 1.0F, 1.0F, 1.0F);
        Mc.bindTexture(mc, skin);
        Mc.blit(left, top, 8.0F, 8.0F, 8, 8, FACE, FACE, 64.0F, 64.0F);    // face
        Mc.blit(left, top, 40.0F, 8.0F, 8, 8, FACE, FACE, 64.0F, 64.0F);   // hat layer

        if (edge) drawArrow(x + dx * 13.0, y + dy * 13.0, Math.toDegrees(Math.atan2(dy, dx)));

        drawDistance(mc, String.valueOf(metres), x, top + FACE + 2);
    }

    /** Small triangle pointing the way you would turn. */
    private void drawArrow(double x, double y, double angleDeg) {
        Mc.pushMatrix();
        Mc.translate((float) x, (float) y, 0.0F);
        Mc.rotate((float) angleDeg, 0.0F, 0.0F, 1.0F);
        Mc.disableTexture();
        Mc.triangle(6.0F, 0.0F, -3.0F, -4.0F, -3.0F, 4.0F, 1.0F, 1.0F, 1.0F, 1.0F);
        Mc.enableTexture();
        Mc.popMatrix();
    }

    /** Distance in whole metres, outlined on all sides so it reads against any background. */
    private void drawDistance(Minecraft mc, String text, double centreX, double topY) {
        Mc.pushMatrix();
        Mc.translate((float) centreX, (float) topY, 0.0F);
        Mc.scale(0.75F, 0.75F, 1.0F);
        int x = -Mc.stringWidth(mc, text) / 2;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                if (ox != 0 || oy != 0) Mc.drawString(mc, text, x + ox, oy, 0xFF000000);
            }
        }
        Mc.drawString(mc, text, x, 0, 0xFFFFFFFF);
        Mc.popMatrix();
    }

    private static double lerp(double from, double to, float t) { return from + (to - from) * t; }

    private static float lerpAngle(float from, float to, float t) {
        float d = to - from;
        while (d < -180.0F) d += 360.0F;
        while (d >= 180.0F) d -= 360.0F;
        return from + d * t;
    }
}
