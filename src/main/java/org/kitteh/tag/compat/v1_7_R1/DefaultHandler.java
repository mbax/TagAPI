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
package org.kitteh.tag.compat.v1_7_R1;

import java.lang.reflect.Field;

import net.minecraft.server.v1_7_R1.NetworkManager;
import net.minecraft.server.v1_7_R1.PacketPlayOutNamedEntitySpawn;
import net.minecraft.util.com.mojang.authlib.GameProfile;

import org.bukkit.craftbukkit.v1_7_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.kitteh.tag.api.PacketHandlerException;
import org.kitteh.tag.api.PacketHandlerNetty;
import org.kitteh.tag.api.TagHandler;

public class DefaultHandler extends PacketHandlerNetty {

    private Field channelField;
    private Field entityIDField;
    private Field gameProfileField;

    public DefaultHandler(TagHandler handler) throws PacketHandlerException {
        super(handler);
    }

    @Override
    public void handlePacket(Object packet, Player destination) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        if (packet instanceof PacketPlayOutNamedEntitySpawn) {
            final PacketPlayOutNamedEntitySpawn p = (PacketPlayOutNamedEntitySpawn) packet;
            final GameProfile profile = (GameProfile) this.gameProfileField.get(p);
            final String oldName = profile.getName();
            final String newName = this.handler.getNameForPacket20(this.entityIDField.getInt(p), oldName, destination);
            if (!newName.equals(oldName)) {
                this.gameProfileField.set(p, new GameProfile("aaaaaaaaaaaaa", newName)); // TODO: Get the ID?
            }
        }
    }

    @Override
    protected void construct() throws NoSuchFieldException, SecurityException {
        this.channelField = NetworkManager.class.getDeclaredField("k");
        this.channelField.setAccessible(true);
        this.entityIDField = PacketPlayOutNamedEntitySpawn.class.getDeclaredField("a");
        this.entityIDField.setAccessible(true);
        this.gameProfileField = PacketPlayOutNamedEntitySpawn.class.getDeclaredField("b");
        this.gameProfileField.setAccessible(true);
    }

    @Override
    protected Field getChannelField() {
        return this.channelField;
    }

    @Override
    protected Object getNetworkManager(Player player) {
        return ((CraftPlayer) player).getHandle().playerConnection.networkManager;
    }

    @Override
    protected String getVersion() {
        return "v1_7_R1";
    }

}