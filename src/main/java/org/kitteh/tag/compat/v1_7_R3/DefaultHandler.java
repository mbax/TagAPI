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
package org.kitteh.tag.compat.v1_7_R3;

import net.minecraft.server.v1_7_R3.NetworkManager;
import net.minecraft.server.v1_7_R3.PacketPlayOutNamedEntitySpawn;
import net.minecraft.util.com.mojang.authlib.GameProfile;
import net.minecraft.util.com.mojang.authlib.properties.Property;
import org.bukkit.craftbukkit.v1_7_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.kitteh.tag.api.PacketHandlerException;
import org.kitteh.tag.api.PacketHandlerNetty;
import org.kitteh.tag.api.TagHandler;
import org.kitteh.tag.api.TagInfo;

import java.lang.reflect.Field;
import java.util.UUID;

public class DefaultHandler extends PacketHandlerNetty {

    private int tastySnack = 0;
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
            final UUID oldID = profile.getId();
            final TagInfo newName = this.handler.getNameForPacket20(oldID, this.entityIDField.getInt(p), oldName, destination);
            if (newName != null && !newName.getName().equals(oldName)) {
                int i = this.tastySnack++;
                GameProfile newProfile = new GameProfile(newName.getUUID(), newName.getName());
                PropertiesResult.Properties properties = PropertiesResult.getProperties(newName.getUUID().toString().replaceAll("-", ""), false);
                Property property = new Property(properties.name, properties.value, properties.signature);
                newProfile.getProperties().clear();
                newProfile.getProperties().put(property.getName(), property);
                this.gameProfileField.set(p, newProfile);
            }
        }
    }

    @Override
    protected void construct() throws NoSuchFieldException, SecurityException {
        this.channelField = NetworkManager.class.getDeclaredField("m");
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
        return "v1_7_R3";
    }

}
