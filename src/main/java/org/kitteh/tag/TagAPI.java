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

import net.minecraft.server.Packet20NamedEntitySpawn;

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

    public static void packet(Packet20NamedEntitySpawn packet, Player destination) {
        if (TagAPI.instance == null) {
            throw new TagAPIException("TagAPI not loaded");
        }
        TagAPI.instance.handlePacket(packet, destination);
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
        } catch (IOException e) {
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
