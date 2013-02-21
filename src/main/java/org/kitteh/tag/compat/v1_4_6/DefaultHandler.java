package org.kitteh.tag.compat.v1_4_6;

import net.minecraft.server.v1_4_6.Packet20NamedEntitySpawn;

import org.bukkit.craftbukkit.v1_4_6.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.kitteh.tag.api.PacketHandler;
import org.kitteh.tag.api.TagHandler;

public class DefaultHandler extends PacketHandler {

    public DefaultHandler(TagHandler handler) {
        super(handler);
    }

    @Override
    protected void construct() throws NoSuchFieldException, SecurityException {
        net.minecraft.server.v1_4_6.EntityPlayer.class.getDeclaredField("playerConnection");
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
        return "v1_4_6";
    }

    @Override
    protected void handlePacketAdd(Object o, Player owner) {
        if (o instanceof Packet20NamedEntitySpawn) {
            final Packet20NamedEntitySpawn packet = ((Packet20NamedEntitySpawn) o);
            packet.b = this.handler.packet(packet.a, packet.b, owner);
        }
    }
}
