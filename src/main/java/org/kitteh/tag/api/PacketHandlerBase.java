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
package org.kitteh.tag.api;

import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public abstract class PacketHandlerBase implements IPacketHandler {

    public class HandlerListener implements Listener {

        private final PacketHandlerBase handler;

        public HandlerListener(PacketHandlerBase handler) {
            this.handler = handler;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerJoin(PlayerJoinEvent event) {
            this.handler.hookPlayer(event.getPlayer());
        }

    }

    protected final TagHandler handler;
    protected final Plugin plugin;

    public PacketHandlerBase(TagHandler handler) {
        this.plugin = handler.getPlugin();
        this.handler = handler;
        this.plugin.getServer().getPluginManager().registerEvents(new HandlerListener(this), this.plugin);
        try {
            this.construct();
        } catch (final Exception e) {
            if (this.plugin.getServer().getName().equals("CraftBukkit")) {
                this.plugin.getLogger().log(Level.SEVERE, "Found CraftBukkit " + this.getVersion() + " but something is wrong.", e);
            } else {
                this.plugin.getLogger().log(Level.SEVERE, "Not currently compatible with mod " + this.plugin.getName(), e);
            }
            this.plugin.getServer().getPluginManager().disablePlugin(this.plugin);
            return;
        }
    }

    @Override
    public void shutdown() {
        for (final Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (player != null) {
                this.releasePlayer(player);
            }
        }
    }

    @Override
    public void startup() {
        for (final Player player : this.plugin.getServer().getOnlinePlayers()) {
            this.hookPlayer(player);
        }
    }

    protected abstract void construct() throws NoSuchFieldException, SecurityException;

    protected abstract String getVersion();

    protected abstract void hookPlayer(Player player);

    protected abstract void releasePlayer(Player player);

}