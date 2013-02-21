package org.kitteh.tag.handler;

import org.kitteh.tag.TagAPI;
import org.kitteh.tag.api.IPacketHandler;

import com.comphenix.protocol.Packets;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ConnectionSide;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.FieldAccessException;

public class ProtocolLibHandler implements IPacketHandler {

    private final TagAPI plugin;

    public ProtocolLibHandler(TagAPI plugin) {
        this.plugin = plugin;
    }

    @Override
    public void shutdown() {
        ProtocolLibrary.getProtocolManager().removePacketListeners(this.plugin);
    }

    @Override
    public void startup() {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this.plugin, ConnectionSide.SERVER_SIDE, ListenerPriority.HIGH, Packets.Server.NAMED_ENTITY_SPAWN) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPacketID() != Packets.Server.NAMED_ENTITY_SPAWN) {
                    return;
                }
                final PacketContainer packetContainer = event.getPacket();
                try {
                    String newName = ProtocolLibHandler.this.plugin.packet(packetContainer.getSpecificModifier(int.class).read(0), packetContainer.getSpecificModifier(String.class).read(0), event.getPlayer());
                    packetContainer.getSpecificModifier(String.class).write(0, newName);
                } catch (final FieldAccessException e) {
                    e.printStackTrace();
                }
            }
        });
    }

}
