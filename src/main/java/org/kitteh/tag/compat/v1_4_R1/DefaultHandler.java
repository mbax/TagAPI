package org.kitteh.tag.compat.v1_4_R1;

import net.minecraft.server.v1_4_R1.Packet20NamedEntitySpawn;

import org.bukkit.craftbukkit.v1_4_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.kitteh.tag.api.Packet;
import org.kitteh.tag.api.PacketHandler;
import org.kitteh.tag.api.TagHandler;

public class DefaultHandler extends PacketHandler {

    public DefaultHandler(TagHandler handler) {
        super(handler);
    }

    @Override
    protected void construct() throws NoSuchFieldException, SecurityException {
        net.minecraft.server.v1_4_R1.EntityPlayer.class.getDeclaredField("playerConnection");
    }

    @Override
    protected Object getNetworkManager(Player player) {
        return ((CraftPlayer) player).getHandle().playerConnection.networkManager;
    }

    @Override
    protected String getQueueField() {
        return "highPriorityQueue";
    }

    @Override
    protected String getVersion() {
        return "v1_4_R1";
    }

    @Override
    protected void handlePacketAdd(Object o, Player owner) {
        if (o instanceof Packet20NamedEntitySpawn) {
            final Packet20NamedEntitySpawn packet = ((Packet20NamedEntitySpawn) o);
            final Packet p = new Packet(packet.b, packet.a);
            this.handler.packet(p, owner);
            packet.b = p.tag;
        }
    }
}
