package friendfinder.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Client to server: the complete set of people this player is willing to share with.
 *
 * Always the whole set, never a delta, so a dropped packet cannot leave the server
 * with a stale idea of who consented. Re-sent on join and alongside each hello, which
 * is what lets a restarted server rebuild every player's picks without anyone
 * reconnecting.
 */
public class SelectionPacket implements IMessage {

    public UUID from;
    public Set<UUID> picks = new HashSet<UUID>();

    public SelectionPacket() {}

    public SelectionPacket(UUID from, Set<UUID> picks) {
        this.from = from;
        this.picks = new HashSet<UUID>(picks);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(from.getMostSignificantBits());
        buf.writeLong(from.getLeastSignificantBits());
        buf.writeShort(picks.size());
        for (UUID id : picks) {
            buf.writeLong(id.getMostSignificantBits());
            buf.writeLong(id.getLeastSignificantBits());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        from = new UUID(buf.readLong(), buf.readLong());
        int n = buf.readUnsignedShort();
        List<UUID> read = new ArrayList<UUID>(n);
        for (int i = 0; i < n; i++) read.add(new UUID(buf.readLong(), buf.readLong()));
        picks = new HashSet<UUID>(read);
    }

    /** Runs on the network thread; Selections is concurrent. */
    public static class Handler implements IMessageHandler<SelectionPacket, IMessage> {
        @Override
        public IMessage onMessage(SelectionPacket msg, MessageContext ctx) {
            if (msg.from != null) Selections.set(msg.from, msg.picks);
            return null;
        }
    }
}
