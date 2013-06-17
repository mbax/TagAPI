/*
 * Copyright 2012 Matt Baxter
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
package org.kitteh.tag.compat.v1_5_R3;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.v1_5_R3.DataWatcher;
import net.minecraft.server.v1_5_R3.Packet20NamedEntitySpawn;
import net.minecraft.server.v1_5_R3.Packet24MobSpawn;
import net.minecraft.server.v1_5_R3.Packet40EntityMetadata;
import net.minecraft.server.v1_5_R3.WatchableObject;

import org.bukkit.craftbukkit.v1_5_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.kitteh.tag.EntityNameResult;
import org.kitteh.tag.PlayerReceiveEntityNameTagEvent;
import org.kitteh.tag.api.PacketHandler;
import org.kitteh.tag.api.TagHandler;

public class DefaultHandler extends PacketHandler {
    private Field dataWatcherMap;
    private Field packet24DataWatcher;
    private Field packet40List;

    public DefaultHandler(TagHandler handler) {
        super(handler);
    }

    @Override
    protected void construct() throws NoSuchFieldException, SecurityException {
        this.dataWatcherMap = DataWatcher.class.getDeclaredField("c");
        this.dataWatcherMap.setAccessible(true);
        this.packet24DataWatcher = Packet24MobSpawn.class.getDeclaredField("t");
        this.packet24DataWatcher.setAccessible(true);
        this.packet40List = Packet40EntityMetadata.class.getDeclaredField("b");
        this.packet40List.setAccessible(true);
        net.minecraft.server.v1_5_R3.EntityPlayer.class.getDeclaredField("playerConnection");
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
        return "v1_5_R3";
    }

    @Override
    protected void handlePacketAdd(Object o, Player owner) {
        if (o instanceof Packet20NamedEntitySpawn) {
            final Packet20NamedEntitySpawn packet = ((Packet20NamedEntitySpawn) o);
            packet.b = this.handler.getNameForPacket20(packet.a, packet.b, owner);
        } else if (o instanceof Packet24MobSpawn) {
            if (PlayerReceiveEntityNameTagEvent.getHandlerList().getRegisteredListeners().length == 0) {
                return;
            }
            final Packet24MobSpawn packet = ((Packet24MobSpawn) o);
            try {
                final DataWatcher oldWatcher = (DataWatcher) this.packet24DataWatcher.get(packet);
                final Map<?, ?> oldMap = (Map<?, ?>) this.dataWatcherMap.get(oldWatcher);
                final Map<Object, Object> newMap = new HashMap<Object, Object>(oldMap);
                final WatchableObject oldWOName = (WatchableObject) oldMap.get(Integer.valueOf(5));
                final String oldName = (String) oldWOName.b();
                final WatchableObject oldWOVisible = (WatchableObject) oldMap.get(Integer.valueOf(6));
                final boolean oldVisible = ((Byte) oldWOVisible.b()) == 1;
                final EntityNameResult result = this.handler.getNameForEntity(packet.a, owner);
                if (result == null) {
                    return;
                }
                final String newName = result.getTag();
                final boolean newVisible = result.isTagVisible();
                if (oldName.equals(newName) && (oldVisible == newVisible)) {
                    return;
                }
                final WatchableObject newWOName = new WatchableObject(oldWOName.c(), oldWOName.a(), newName);
                newWOName.a(oldWOName.d());
                final WatchableObject newWOVisible = new WatchableObject(oldWOVisible.c(), oldWOVisible.a(), (byte) (newVisible ? 1 : 0));
                newWOName.a(oldWOVisible.d());
                newMap.put(Integer.valueOf(5), newWOName);
                newMap.put(Integer.valueOf(6), newWOVisible);
                final DataWatcher newWatcher = new DataWatcher();
                this.dataWatcherMap.set(newWatcher, newMap);
                this.packet24DataWatcher.set(packet, newWatcher);
            } catch (final Exception e) {
                return;
            }
        } else if (o instanceof Packet40EntityMetadata) {
            if (PlayerReceiveEntityNameTagEvent.getHandlerList().getRegisteredListeners().length == 0) {
                return;
            }
            final Packet40EntityMetadata packet = ((Packet40EntityMetadata) o);
            try {
                @SuppressWarnings("unchecked")
                final List<Object> list = (List<Object>) this.packet40List.get(packet);
                WatchableObject oldWOName = null;
                WatchableObject oldWOVisible = null;
                for (final Object w : list) {
                    final WatchableObject watchable = (WatchableObject) w;
                    if (watchable.a() == 5) {
                        oldWOName = watchable;
                    } else if (watchable.a() == 6) {
                        oldWOVisible = watchable;
                    }
                }
                final EntityNameResult result = this.handler.getNameForEntity(packet.a, owner);
                if (result == null) {
                    return;
                }
                if (result.isTagModified()) {
                    if (oldWOName != null) {
                        list.remove(oldWOName);
                    }
                    list.add(new WatchableObject(4, 5, result.getTag()));
                }
                if (result.isVisibleModified()) {
                    if (oldWOVisible != null) {
                        list.remove(oldWOVisible);
                    }
                    list.add(new WatchableObject(0, 6, (byte) (result.isTagVisible() ? 1 : 0)));
                }
            } catch (final Exception e) {
                return;
            }
        }
    }
}