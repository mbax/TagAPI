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

import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

import net.minecraft.server.EntityHuman;
import net.minecraft.server.Packet20NamedEntitySpawn;
import net.minecraft.server.Packet29DestroyEntity;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.kitteh.tag.metrics.MetricsLite;

public class TagAPI extends JavaPlugin implements Listener {

    private HashMap<Integer, String> entityIDMap;

    private static TagAPI instance = null;

    /**
     * Do not touch me. Seriously. Do not touch this method.
     * 
     * @param packet
     * @param destination
     */
    public static void packet(Packet20NamedEntitySpawn packet, Player destination) {
        if (TagAPI.instance == null) {
            throw new TagAPIException("TagAPI not loaded");
        }
        TagAPI.instance.handlePacket(packet, destination);
    }

    /**
     * Flicker the player for anyone who can see him.
     * 
     * @param player
     */
    public static void refreshPlayer(Player player) {
        if (TagAPI.instance == null) {
            throw new TagAPIException("Can't fire TagAPI method while TagAPI is disabled!");
        }
        if (player == null) {
            throw new TagAPIException("Can't submit null player!");
        }
        if (!player.isOnline()) {
            throw new TagAPIException("Can't submit offline player!");
        }
        final int id = player.getEntityId();
        final EntityHuman human = ((CraftPlayer) player).getHandle();
        for (final Player otherGuy : player.getWorld().getPlayers()) {
            if (otherGuy.canSee(player)) {
                final CraftPlayer otherGuyC = (CraftPlayer) otherGuy;
                otherGuyC.getHandle().netServerHandler.sendPacket(new Packet29DestroyEntity(id));
                otherGuyC.getHandle().netServerHandler.sendPacket(new Packet20NamedEntitySpawn(human));
            }
        }
    }

    /**
     * Flicker the player for a player who can see him.
     * 
     * @param player
     * @param forWhom
     */
    public static void refreshPlayer(Player player, Player forWhom) {
        if (TagAPI.instance == null) {
            throw new TagAPIException("Can't fire TagAPI method while TagAPI is disabled!");
        }
        if (player == null) {
            throw new TagAPIException("Can't submit null player!");
        }
        if (forWhom == null) {
            throw new TagAPIException("Can't submit null forWhom!");
        }
        if (forWhom.canSee(player) && player.getWorld().equals(forWhom.getWorld())) {
            final int id = player.getEntityId();
            final EntityHuman human = ((CraftPlayer) player).getHandle();
            final CraftPlayer otherGuyC = (CraftPlayer) forWhom;
            otherGuyC.getHandle().netServerHandler.sendPacket(new Packet29DestroyEntity(id));
            otherGuyC.getHandle().netServerHandler.sendPacket(new Packet20NamedEntitySpawn(human));
        }
    }

    /**
     * Flicker the player for anyone in a set of players who can see him.
     * 
     * @param player
     * @param forWhom
     */
    public static void refreshPlayer(Player player, Set<Player> forWhom) {
        if (TagAPI.instance == null) {
            throw new TagAPIException("Can't fire TagAPI method while TagAPI is disabled!");
        }
        if (player == null) {
            throw new TagAPIException("Can't submit null player!");
        }
        if ((forWhom == null) || forWhom.isEmpty()) {
            throw new TagAPIException("Can't submit empty forWhom!");
        }
        for (final Player whom : forWhom) {
            if (whom != null) {
                TagAPI.refreshPlayer(player, whom);
            }
        }
    }

    @Override
    public void onDisable() {
        for (final Player player : this.getServer().getOnlinePlayers()) {
            if (player != null) {
                this.out(player);
            }
        }
        ArrayLizt.disable();
        TagAPI.instance = null;
    }

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
        this.entityIDMap = new HashMap<Integer, String>();
        TagAPI.instance = this;
        ArrayLizt.enable();
        for (final Player player : this.getServer().getOnlinePlayers()) {
            this.in(player);
        }
        try {
            new MetricsLite(this).start();
        } catch (final IOException e) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.in(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.entityIDMap.remove(event.getPlayer().getEntityId());
    }

    private void handlePacket(Packet20NamedEntitySpawn packet, Player destination) {
        final String packetName = this.entityIDMap.get(packet.a);
        if (packetName == null) {
            this.getLogger().fine("Encountered a packet with an unknown entityID. Discarded. ID " + packet.a);
            return;
        }
        final Player named = this.getServer().getPlayerExact(packetName);
        if (named == null) {
            this.getLogger().fine("Player " + packetName + " seems to have logged off during packet sending. Discarded.");
            return;
        }
        if (destination == null) {
            this.getLogger().fine("Encountered a packet destined for an unknown player. Discarded.");
            return;
        }
        final PlayerReceiveNameTagEvent event = new PlayerReceiveNameTagEvent(destination, named);
        this.getServer().getPluginManager().callEvent(event);
        if (event.isModified()) {
            String name = event.getTag();
            if (name.length() > 16) {
                name = name.substring(0, 16);
            }
            packet.b = name;
        }
    }

    private void in(Player player) {
        this.entityIDMap.put(player.getEntityId(), player.getName());
        ArrayLizt.inject(player);
    }

    private void out(Player player) {
        ArrayLizt.outject(player);
    }

}
