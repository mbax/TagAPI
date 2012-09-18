package org.kitteh.tag.handler;

import net.minecraft.server.Packet;
import net.minecraft.server.Packet20NamedEntitySpawn;

import org.kitteh.tag.TagAPI;
import org.kitteh.tag.TagAPIException;

import com.comphenix.protocol.Packets;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ConnectionSide;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;

public class ProtocolLibHandler implements PacketHandler {

    private TagAPI plugin;

    public ProtocolLibHandler(TagAPI plugin) {
        this.plugin = plugin;
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this.plugin, ConnectionSide.SERVER_SIDE, ListenerPriority.NORMAL, Packets.Server.NAMED_ENTITY_SPAWN) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPacketID() != Packets.Server.NAMED_ENTITY_SPAWN) {
                    return;
                }
                final Packet packet = event.getPacket().getHandle();
                if (packet instanceof Packet20NamedEntitySpawn) {
                    ProtocolLibHandler.this.plugin.packet((Packet20NamedEntitySpawn) packet, event.getPlayer());
                } else {
                    throw new TagAPIException("Got a packet of type " + packet.getClass() + " instead of expected type");
                }
            }
        });
    }

    @Override
    public void shutdown() {
        ProtocolLibrary.getProtocolManager().removePacketListeners(this.plugin);
    }

}
