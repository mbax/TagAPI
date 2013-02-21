package org.kitteh.tag.compat.pre;

import net.minecraft.server.Packet20NamedEntitySpawn;

import org.bukkit.craftbukkit.entity.CraftPlayer;
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
        net.minecraft.server.EntityPlayer.class.getDeclaredField("netServerHandler");
    }

    @Override
    protected Object getNetworkManager(Player player) {
        return ((CraftPlayer) player).getHandle().netServerHandler.networkManager;
    }

    @Override
    protected String getQueueField() {
        return "highPriorityQueue";
    }

    @Override
    protected String getVersion() {
        return "1.4";
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
