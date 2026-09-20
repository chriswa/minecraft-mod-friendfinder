package friendfinder;

import com.mojang.authlib.GameProfile;
import friendfinder.net.Friends;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The list screen: everyone online, and who is sharing with whom.
 *
 * One list rather than several, because a person moves between categories as either
 * side ticks a box and watching a row jump between separate lists is harder to follow
 * than watching it move within one. Order is: sharing, then waiting on them, then
 * waiting on you, then everyone else; alphabetical inside each group. The row colour
 * says which group it is in, the two ticks say who has agreed — yours on the left,
 * theirs on the right, in the same order as the sentence at the top of the screen.
 *
 * Rebuilt every frame from live state, so it reflects people joining, leaving, and
 * ticking you while it is open, with no refresh button and nothing to invalidate.
 */
public class GuiFriends extends GuiScreen {

    private static final int ROW_H = 22;
    private static final int PAD = 8;
    private static final int SCROLLBAR_W = 4;

    // Row backgrounds, in the order the list sorts.
    private static final int C_MUTUAL = 0x3341C86B;   // sharing both ways
    private static final int C_YOURS  = 0x33D9A441;   // you ticked, they have not
    private static final int C_THEIRS = 0x334C8CE0;   // they ticked, you have not
    private static final int C_NONE   = 0x22101010;   // neither
    private static final int C_HOVER  = 0x22FFFFFF;
    private static final int TICK_ON  = 0xFF41C86B;
    private static final int TICK_OFF = 0xFF2A2A2A;
    private static final int TICK_EDGE = 0xFF8A8A8A;

    private int panelX, panelY, panelW, panelH, listTop, listBottom;
    private float scroll;
    private boolean draggingBar;
    private GuiTextField filter;
    private final List<Row> rows = new ArrayList<Row>();

    private static final class Row {
        final UUID id; final String name; final boolean mine, theirs;
        Row(UUID id, String name, boolean mine, boolean theirs) {
            this.id = id; this.name = name; this.mine = mine; this.theirs = theirs;
        }
        int rank() { return mine ? (theirs ? 0 : 1) : (theirs ? 2 : 3); }
        int colour() {
            switch (rank()) {
                case 0: return C_MUTUAL;
                case 1: return C_YOURS;
                case 2: return C_THEIRS;
                default: return C_NONE;
            }
        }
    }

    /** initGui */
    @Override
    public void func_73866_w_() {
        panelW = Math.min(340, this.field_146294_l - 40);
        panelH = Math.min(240, this.field_146295_m - 40);
        panelX = (this.field_146294_l - panelW) / 2;
        panelY = (this.field_146295_m - panelH) / 2;
        listTop = panelY + 52;
        listBottom = panelY + panelH - 20;

        // GuiTextField(id, fontRenderer, x, y, width, height)
        filter = new GuiTextField(0, this.field_146289_q, panelX + PAD, panelY + 32,
                panelW - PAD * 2, 14);
        filter.func_146203_f(32);           // setMaxStringLength
        filter.func_146195_b(true);         // focused: you can just type to filter
    }

    /** updateScreen — keeps the text cursor blinking */
    @Override
    public void func_73876_c() {
        if (filter != null) filter.func_146178_a();
    }

    /** doesGuiPauseGame: no, the world should keep running behind it */
    @Override
    public boolean func_73868_f() {
        return false;
    }

    /** drawScreen */
    @Override
    public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
        this.func_146276_q_();      // drawDefaultBackground

        rebuild();
        clampScroll();
        if (draggingBar && !Mouse.isButtonDown(0)) draggingBar = false;
        if (draggingBar) dragTo(mouseY);

        Mc.rect(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0101010);
        Mc.rect(panelX, panelY, panelX + panelW, panelY + 1, 0xFF3A3A3A);
        Mc.rect(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFF3A3A3A);

        Mc.drawString(this.field_146297_k, "Friend Finder", panelX + PAD, panelY + 8, 0xFFFFFFFF);
        String rule = Friends.serverAware()
                ? "\u00a77Tick them, they tick you, then you share.  \u00a78left box: you  right box: them"
                : "\u00a77No Friend Finder on this server - nearby players show anyway.";
        Mc.drawString(this.field_146297_k, rule, panelX + PAD, panelY + 20, 0xFFAAAAAA);

        filter.func_146194_f();     // drawTextBox
        if (filter.func_146179_b().isEmpty()) {
            Mc.drawString(this.field_146297_k, "§8type to search", panelX + PAD + 4, panelY + 35, 0xFF666666);
        }

        drawList(mouseX, mouseY);

        int shown = rows.size();
        Mc.drawString(this.field_146297_k, "§7" + shown + (shown == 1 ? " player" : " players"),
                panelX + PAD, panelY + panelH - 14, 0xFF888888);
        String hint = "§8click to tick, esc to close";
        Mc.drawString(this.field_146297_k, hint,
                panelX + panelW - PAD - Mc.stringWidth(this.field_146297_k, hint), panelY + panelH - 14, 0xFF666666);

        super.func_73863_a(mouseX, mouseY, partialTicks);
    }

    private void drawList(int mouseX, int mouseY) {
        int w = panelW - PAD * 2;
        int x = panelX + PAD;
        Mc.rect(x, listTop, x + w, listBottom, 0x40000000);

        // Clip to the list box: GL scissor works in framebuffer pixels, measured from
        // the bottom, so gui coordinates get scaled and flipped.
        int sf = Mc.scaleFactor(this.field_146297_k);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * sf, Mc.displayHeight(this.field_146297_k) - listBottom * sf,
                w * sf, (listBottom - listTop) * sf);

        int y = listTop - (int) scroll;
        for (Row row : rows) {
            if (y + ROW_H >= listTop && y <= listBottom) drawRow(row, x, y, w, mouseX, mouseY);
            y += ROW_H;
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        drawScrollbar(x + w - SCROLLBAR_W, w);
    }

    private void drawRow(Row row, int x, int y, int w, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= Math.max(y, listTop)
                && mouseY <= Math.min(y + ROW_H, listBottom) && mouseY >= listTop && mouseY <= listBottom;
        Mc.rect(x, y, x + w, y + ROW_H - 1, row.colour());
        if (hover) Mc.rect(x, y, x + w, y + ROW_H - 1, C_HOVER);

        drawTick(x + 5, y + 6, row.mine);

        ResourceLocation skin = Mc.skinFor(this.field_146297_k, row.id);
        if (skin != null) {
            Mc.enableBlend();
            Mc.blendFunc();
            Mc.enableTexture();
            Mc.colour(1.0F, 1.0F, 1.0F, 1.0F);
            Mc.bindTexture(this.field_146297_k, skin);
            Mc.blit(x + 21, y + 3, 8.0F, 8.0F, 8, 8, 16, 16, 64.0F, 64.0F);
            Mc.blit(x + 21, y + 3, 40.0F, 8.0F, 8, 8, 16, 16, 64.0F, 64.0F);
        }

        int nameLeft = x + 43;
        int nameRight = x + w - 28;
        String name = trim(row.name, nameRight - nameLeft);
        Mc.drawString(this.field_146297_k, name, nameLeft, y + 7, 0xFFFFFFFF);

        drawTick(x + w - 22, y + 6, row.theirs);
    }

    /** Your tick on the left of a row, theirs on the right; filled means yes. */
    private void drawTick(int x, int y, boolean on) {
        Mc.rect(x, y, x + 10, y + 10, TICK_EDGE);
        Mc.rect(x + 1, y + 1, x + 9, y + 9, on ? TICK_ON : TICK_OFF);
        if (on) {
            // A small check, drawn as two strokes so it needs no font glyph.
            Mc.rect(x + 3, y + 5, x + 5, y + 7, 0xFF0C2C18);
            Mc.rect(x + 5, y + 3, x + 7, y + 6, 0xFF0C2C18);
        }
    }

    private void drawScrollbar(int x, int listW) {
        int viewH = listBottom - listTop;
        int contentH = rows.size() * ROW_H;
        if (contentH <= viewH) return;
        Mc.rect(x, listTop, x + SCROLLBAR_W, listBottom, 0x40000000);
        int thumbH = Math.max(16, (int) ((float) viewH / contentH * viewH));
        int travel = viewH - thumbH;
        int thumbY = listTop + (int) (scroll / (contentH - viewH) * travel);
        Mc.rect(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, 0xFF7A7A7A);
    }

    private String trim(String name, int max) {
        if (Mc.stringWidth(this.field_146297_k, name) <= max) return name;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            if (Mc.stringWidth(this.field_146297_k, sb.toString() + name.charAt(i) + "...") > max) break;
            sb.append(name.charAt(i));
        }
        return sb + "...";
    }

    // ------------------------------------------------------------------ state

    /** Everyone online except you, sorted into the four groups. */
    private void rebuild() {
        rows.clear();
        NetHandlerPlayClient conn = Mc.connection(this.field_146297_k);
        if (conn == null) return;
        UUID me = Mc.uuid(Mc.self(this.field_146297_k));
        String needle = filter == null ? "" : filter.func_146179_b().toLowerCase();

        for (NetworkPlayerInfo info : conn.func_175106_d()) {      // getPlayerInfoMap
            GameProfile profile = info.func_178845_a();            // getGameProfile
            if (profile == null || profile.getId() == null) continue;
            UUID id = profile.getId();
            if (id.equals(me)) continue;
            String name = profile.getName();
            if (name == null || name.isEmpty()) continue;
            if (!needle.isEmpty() && !name.toLowerCase().contains(needle)) continue;
            Friends.remember(id, name);
            rows.add(new Row(id, name, Friends.isMine(id), Friends.hasPickedMe(id)));
        }
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                int byGroup = a.rank() - b.rank();
                return byGroup != 0 ? byGroup : a.name.compareToIgnoreCase(b.name);
            }
        });
    }

    private void clampScroll() {
        float max = Math.max(0, rows.size() * ROW_H - (listBottom - listTop));
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
    }

    private void dragTo(int mouseY) {
        int viewH = listBottom - listTop;
        int contentH = rows.size() * ROW_H;
        if (contentH <= viewH) return;
        float fraction = (float) (mouseY - listTop) / viewH;
        scroll = fraction * (contentH - viewH);
        clampScroll();
    }

    // ------------------------------------------------------------------ input

    /**
     * mouseClicked. Note the stripped signature: the obfuscator removes Exceptions
     * attributes, so the real GuiScreen declares no checked exceptions and neither
     * can an override of it.
     */
    @Override
    public void func_73864_a(int mouseX, int mouseY, int button) {
        filter.func_146192_a(mouseX, mouseY, button);      // GuiTextField.mouseClicked

        int x = panelX + PAD, w = panelW - PAD * 2;
        if (button == 0 && mouseX >= x + w - SCROLLBAR_W && mouseX <= x + w
                && mouseY >= listTop && mouseY <= listBottom) {
            draggingBar = true;
            dragTo(mouseY);
            return;
        }
        if (button == 0 && mouseX >= x && mouseX <= x + w && mouseY >= listTop && mouseY <= listBottom) {
            int index = (int) ((mouseY - listTop + scroll) / ROW_H);
            if (index >= 0 && index < rows.size()) {
                Row row = rows.get(index);
                Friends.toggle(row.id, row.name);
                FriendFinder.sendSelection();     // tell the server at once, not on the next tick
            }
        }
    }

    /** handleMouseInput — the wheel */
    @Override
    public void func_146274_d() {
        super.func_146274_d();
        // getEventDWheel, not getDWheel: this runs inside Minecraft's mouse event loop,
        // where the accumulator the latter reads is not the right source.
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll -= (wheel > 0 ? 1 : -1) * ROW_H * 2;
            clampScroll();
        }
    }

    /** keyTyped */
    @Override
    public void func_73869_a(char typed, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            this.field_146297_k.func_147108_a(null);      // displayGuiScreen(null): close
            return;
        }
        filter.func_146201_a(typed, key);                // GuiTextField.textboxKeyTyped
    }
}
