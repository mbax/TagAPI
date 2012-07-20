/* Copyright 2012 Matt Baxter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kitteh.tag;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.kitteh.tag.TagAPI;

import net.minecraft.server.NetworkManager;
import net.minecraft.server.Packet20NamedEntitySpawn;

@SuppressWarnings({ "unchecked", "serial", "rawtypes" })
public class ArrayLizt extends ArrayList {
    private static Field syncField;
    private static Field highField;
    private static boolean go = true;
    private static boolean enabled = false;

    static {
        try {
            ArrayLizt.syncField = NetworkManager.class.getDeclaredField("g");
            ArrayLizt.syncField.setAccessible(true);
            ArrayLizt.highField = NetworkManager.class.getDeclaredField("highPriorityQueue");
            ArrayLizt.highField.setAccessible(true);
        } catch (final Exception e) {
            ArrayLizt.go = false;
            throw new TagAPIException("Failed to prepare Tag API. Disabling...", e);
        }
    }

    public static void disable() {
        ArrayLizt.enabled = false;
    }

    public static void enable() {
        ArrayLizt.enabled = true;
    }

    public static void inject(Player player) {
        if (!ArrayLizt.go || !ArrayLizt.enabled) {
            return;
        }
        final NetworkManager nm = ArrayLizt.getManager(player);
        try {
            ArrayLizt.nom(nm, Collections.synchronizedList(new ArrayLizt(player)));
        } catch (final Exception e) {
            ArrayLizt.go = false;
            throw new TagAPIException("[TagAPI] Failed to inject into networkmanager.", e);
        }
    }

    public static void outject(Player player) {
        if (!ArrayLizt.enabled) {
            return;
        }
        final NetworkManager nm = ArrayLizt.getManager(player);
        try {
            ArrayLizt.nom(nm, Collections.synchronizedList(new ArrayList()));
        } catch (final Exception e) {
            throw new TagAPIException("[TagAPI] Failed to update networkmanager on disable. Could be a problem.", e);
        }
    }

    private static NetworkManager getManager(Player player) {
        return ((CraftPlayer) player).getHandle().netServerHandler.networkManager;
    }

    private static void nom(NetworkManager nm, List list) throws IllegalArgumentException, IllegalAccessException {
        final List old = (List) ArrayLizt.highField.get(nm);
        synchronized (ArrayLizt.syncField.get(nm)) {
            for (final Object object : old) {
                list.add(object);
            }
            ArrayLizt.highField.set(nm, list);
        }
    }

    private final Player owner;

    public ArrayLizt(Player owner) {
        this.owner = owner;
    }

    @Override
    public boolean add(Object o) {
        if (o instanceof Packet20NamedEntitySpawn) {
            TagAPI.packet(((Packet20NamedEntitySpawn) o), this.owner);
        }
        return super.add(o);
    }
}
