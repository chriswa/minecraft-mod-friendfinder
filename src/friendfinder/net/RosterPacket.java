package friendfinder.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server to client: which online players have picked you.
 *
 * This is the only thing the server volunteers about other people's choices, and it
 * is what the list screen shows as their tick. Sent whenever anyone's picks change
 * and whenever somebody joins or leaves, so the screen updates while it is open.
 */
public class RosterPacket implements IMessage {

    public Set<UUID> pickedMe = new HashSet<UUID>();

    public RosterPacket() {}

    public RosterPacket(Set<UUID> pickedMe) {
        this.pickedMe = new HashSet<UUID>(pickedMe);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(pickedMe.size());
        for (UUID id : pickedMe) {
            buf.writeLong(id.getMostSignificantBits());
            buf.writeLong(id.getLeastSignificantBits());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int n = buf.readUnsignedShort();
        pickedMe = new HashSet<UUID>(n);
        for (int i = 0; i < n; i++) pickedMe.add(new UUID(buf.readLong(), buf.readLong()));
    }

    /** Runs on the network thread; Friends is concurrent. */
    public static class Handler implements IMessageHandler<RosterPacket, IMessage> {
        @Override
        public IMessage onMessage(RosterPacket msg, MessageContext ctx) {
            Friends.setPickedMeBy(msg.pickedMe);
            return null;
        }
    }
}
