/*
 * Copyright 2012-2014 Matt Baxter
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
package org.kitteh.tag.compat.v1_4_5;

import net.minecraft.server.v1_4_5.Packet20NamedEntitySpawn;

import org.bukkit.craftbukkit.v1_4_5.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.kitteh.tag.api.PacketHandlerException;
import org.kitteh.tag.api.PacketHandlerListInjection;
import org.kitteh.tag.api.TagHandler;
import org.kitteh.tag.api.TagInfo;

public class DefaultHandler extends PacketHandlerListInjection {

    public DefaultHandler(TagHandler handler) throws PacketHandlerException {
        super(handler);
    }

    @Override
    protected void construct() throws NoSuchFieldException, SecurityException {
        net.minecraft.server.v1_4_5.EntityPlayer.class.getDeclaredField("netServerHandler");
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
        return "v1_4_5";
    }

    @Override
    protected void handlePacketAdd(Object o, Player owner) {
        if (o instanceof Packet20NamedEntitySpawn) {
            final Packet20NamedEntitySpawn packet = ((Packet20NamedEntitySpawn) o);
            final TagInfo info = this.handler.getNameForPacket20(null, packet.a, packet.b, owner);
            if (info != null) {
                packet.b = info.getName();
            }
        }
    }

}