package friendfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;
import java.util.List;

/**
 * {@code /friendfinder}, aliased to {@code /ff}, opens the list screen.
 *
 * Registered with Forge's client command handler, so it never reaches the server and
 * works even where the server half is not installed. The long name is the real one:
 * a later mod registering the same command silently replaces it, and {@code /ff} is
 * short enough to be worth claiming that it might one day collide.
 */
public class CommandFriends extends CommandBase {

    /** getName */
    @Override
    public String func_71517_b() {
        return "friendfinder";
    }

    /** getAliases */
    @Override
    public List<String> func_71514_a() {
        return Arrays.asList("ff");
    }

    /** getUsage */
    @Override
    public String func_71518_a(ICommandSender sender) {
        return "/friendfinder — choose who you share your position with";
    }

    /** checkPermission: a client command anyone may run */
    @Override
    public boolean func_184882_a(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    /** execute */
    @Override
    public void func_184881_a(MinecraftServer server, ICommandSender sender, String[] args) {
        final Minecraft mc = Mc.mc();
        // Chat closes itself right after a command runs and would clear whatever we
        // opened, so open the screen on the next tick instead.
        mc.func_152344_a(new Runnable() {
            @Override
            public void run() {
                mc.func_147108_a(new GuiFriends());
            }
        });
    }
}
