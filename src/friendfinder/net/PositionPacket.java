package friendfinder.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server to client: where everyone else is, and how hurt they are.
 *
 * A full snapshot every time, and it is sent even when empty — that is what lets the
 * client drop a marker the moment someone leaves or steps into another dimension.
 * Only players in the recipient's own dimension are ever included, so cross-dimension
 * positions never leave the server.
 *
 * <h3>Two wire formats, and why a mismatch is harmless either way</h3>
 *
 * Protocol 1 carried positions only. Protocol 2 adds a health byte per player, and
 * marks itself with a leading {@link #V2_MARKER} — a value a protocol 1 count byte can
 * never hold, since it is the player count and no server has 255 players in one
 * dimension's snapshot.
 *
 * A new client can therefore read either format. A new server never sends protocol 2
 * to a client that did not ask for it in its {@link HelloPacket}, so an old client is
 * never handed bytes it would misparse. Every combination of old and new works.
 */
public class PositionPacket implements IMessage {

    public static final int PROTOCOL = 2;
    private static final int V2_MARKER = 0xFF;

    /** Health is in half-hearts-worth of points, 0..20, or {@link #UNKNOWN} from a protocol 1 server. */
    public static final float UNKNOWN = -1.0F;
    private static final int HEALTH_MAX = 20;

    public static final class Pos {
        public final UUID id;
        public final double x, y, z;
        public final float health;
        public Pos(UUID id, double x, double y, double z, float health) {
            this.id = id; this.x = x; this.y = y; this.z = z; this.health = health;
        }
    }

    public List<Pos> positions = new ArrayList<Pos>();

    /** Which format to write. Set by the server from what the recipient asked for. */
    public int protocol = PROTOCOL;

    public PositionPacket() {}

    public PositionPacket(int protocol) { this.protocol = protocol; }

    public void add(UUID id, double x, double y, double z, float health) {
        positions.add(new Pos(id, x, y, z, health));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        boolean v2 = protocol >= 2;
        if (v2) buf.writeByte(V2_MARKER);
        buf.writeByte(positions.size());
        for (Pos p : positions) {
            buf.writeLong(p.id.getMostSignificantBits());
            buf.writeLong(p.id.getLeastSignificantBits());
            buf.writeDouble(p.x);
            buf.writeDouble(p.y);
            buf.writeDouble(p.z);
            if (v2) {
                int h = Math.round(p.health);
                buf.writeByte(h < 0 ? 0 : (h > HEALTH_MAX ? HEALTH_MAX : h));
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int first = buf.readUnsignedByte();
        boolean v2 = first == V2_MARKER;
        int n = v2 ? buf.readUnsignedByte() : first;
        positions = new ArrayList<Pos>(n);
        for (int i = 0; i < n; i++) {
            UUID id = new UUID(buf.readLong(), buf.readLong());
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            positions.add(new Pos(id, x, y, z, v2 ? buf.readUnsignedByte() : UNKNOWN));
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
