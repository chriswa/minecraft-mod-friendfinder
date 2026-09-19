package friendfinder.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server to client: where everyone else is, right now.
 *
 * A full snapshot every time, and it is sent even when empty — that is what lets the
 * client drop a marker the moment someone leaves or steps into another dimension.
 * Only players in the recipient's own dimension are ever included, so cross-dimension
 * positions never leave the server.
 */
public class PositionPacket implements IMessage {

    public static final class Pos {
        public final UUID id;
        public final double x, y, z;
        public Pos(UUID id, double x, double y, double z) {
            this.id = id; this.x = x; this.y = y; this.z = z;
        }
    }

    public List<Pos> positions = new ArrayList<Pos>();

    public PositionPacket() {}

    public void add(UUID id, double x, double y, double z) {
        positions.add(new Pos(id, x, y, z));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(positions.size());
        for (Pos p : positions) {
            buf.writeLong(p.id.getMostSignificantBits());
            buf.writeLong(p.id.getLeastSignificantBits());
            buf.writeDouble(p.x);
            buf.writeDouble(p.y);
            buf.writeDouble(p.z);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int n = buf.readUnsignedByte();
        positions = new ArrayList<Pos>(n);
        for (int i = 0; i < n; i++) {
            UUID id = new UUID(buf.readLong(), buf.readLong());
            positions.add(new Pos(id, buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
    }

    /** Runs on the network thread, so it only writes into a concurrent store. */
    public static class Handler implements IMessageHandler<PositionPacket, IMessage> {
        @Override
        public IMessage onMessage(PositionPacket msg, MessageContext ctx) {
            RemotePlayers.accept(msg.positions);
            return null;
        }
    }
}
