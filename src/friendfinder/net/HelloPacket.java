package friendfinder.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

/**
 * Client to server: "I have the mod, send me positions."
 *
 * Without this the server would broadcast to everyone, including players who never
 * installed it. Clients repeat it every few seconds, so a server that restarts picks
 * its subscribers back up on its own.
 *
 * The client names itself rather than the server reading it off the connection: that
 * keeps this whole package free of Minecraft classes, which matters because Forge's
 * own jar refers to them by obfuscated names we deliberately do not compile against.
 * Nothing here is privileged — the worst a forged id achieves is sending someone else
 * a few bytes their client ignores.
 */
public class HelloPacket implements IMessage {

    public UUID id;

    public HelloPacket() {}

    public HelloPacket(UUID id) { this.id = id; }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        id = new UUID(buf.readLong(), buf.readLong());
    }

    /** Runs on the network thread; Subscribers is a concurrent set. */
    public static class Handler implements IMessageHandler<HelloPacket, IMessage> {
        @Override
        public IMessage onMessage(HelloPacket msg, MessageContext ctx) {
            if (msg.id != null) Subscribers.add(msg.id);
            return null;
        }
    }
}
