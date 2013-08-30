/*
 * Copyright 2012-2013 Matt Baxter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
                    final String newName = ProtocolLibHandler.this.plugin.getNameForPacket20(packetContainer.getSpecificModifier(int.class).read(0), packetContainer.getSpecificModifier(String.class).read(0), event.getPlayer());
                    packetContainer.getSpecificModifier(String.class).write(0, newName);
                } catch (final FieldAccessException e) {
                    e.printStackTrace();
                }
            }
        });
    }

}