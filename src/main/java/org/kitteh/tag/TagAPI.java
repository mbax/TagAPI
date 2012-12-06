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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.kitteh.tag.api.Packet;
import org.kitteh.tag.api.IPacketHandler;
import org.kitteh.tag.api.TagAPIException;
import org.kitteh.tag.api.TagHandler;
import org.kitteh.tag.handler.ProtocolLibHandler;
import org.kitteh.tag.metrics.MetricsLite;

public class TagAPI extends JavaPlugin implements TagHandler {

    @SuppressWarnings("unused")
    private class HeyListen implements Listener {

        private final TagAPI api;

        public HeyListen(TagAPI api) {
            this.api = api;
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onPlayerJoin(PlayerJoinEvent event) {
            this.api.in(event.getPlayer());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerQuit(PlayerQuitEvent event) {
            this.api.entityIDMap.remove(event.getPlayer().getEntityId());
        }
    }

    private class RefreshPair {

        private final Player seer;
        private final Player target;

        public RefreshPair(Player seer, Player target) {
            this.seer = seer;
            this.target = target;
        }

        public Player getSeer() {
            return this.seer;
        }

        public Player getTarget() {
            return this.target;
        }
    }

    private class ShowBomb implements Runnable {

        private final ArrayList<RefreshPair> pairs = new ArrayList<RefreshPair>();

        public void queue(RefreshPair pair) {
            this.pairs.add(pair);
        }

        @Override
        public void run() {
            for (final RefreshPair pair : this.pairs) {
                final Player seer = pair.getSeer();
                final Player target = pair.getTarget();
                if ((seer != null) && (target != null)) {
                    seer.showPlayer(target);
                }
            }
        }
    }

    private static TagAPI instance = null;
    private static Thread mainThread = null;

    /**
     * Flicker the player for anyone who can see him.
     * 
     * @param player
     */
    public static void refreshPlayer(Player player) {
        TagAPI.check();
        if (player == null) {
            throw new TagAPIException("Can't submit null player!");
        }
        if (!player.isOnline()) {
            throw new TagAPIException("Can't submit offline player!");
        }
        final ShowBomb bomb = TagAPI.instance.new ShowBomb();
        for (final Player otherGuy : player.getWorld().getPlayers()) {
            if ((!otherGuy.equals(player)) && otherGuy.canSee(player)) {
                otherGuy.hidePlayer(player);
                bomb.queue(TagAPI.instance.new RefreshPair(otherGuy, player));
            }
        }
        TagAPI.instance.getServer().getScheduler().scheduleSyncDelayedTask(TagAPI.instance, bomb, 2);
    }

    /**
     * Flicker the player for a player who can see him.
     * 
     * @param player
     * @param forWhom
     */
    public static void refreshPlayer(Player player, Player forWhom) {
        TagAPI.check();
        if (player == null) {
            throw new TagAPIException("Can't submit null player!");
        }
        if (forWhom == null) {
            throw new TagAPIException("Can't submit null forWhom!");
        }
        if (player.equals(forWhom)) {
            throw new TagAPIException("Can't submit player on self!");
        }
        final ShowBomb bomb = TagAPI.instance.new ShowBomb();
        if (forWhom.canSee(player) && player.getWorld().equals(forWhom.getWorld())) {
            forWhom.hidePlayer(player);
            bomb.queue(TagAPI.instance.new RefreshPair(forWhom, player));
        }
        TagAPI.instance.getServer().getScheduler().scheduleSyncDelayedTask(TagAPI.instance, bomb, 2);
    }

    /**
     * Flicker the player for anyone in a set of players who can see him.
     * 
     * @param player
     * @param forWhom
     */
    public static void refreshPlayer(Player player, Set<Player> forWhom) {
        TagAPI.check();
        if (player == null) {
            throw new TagAPIException("Can't submit null player!");
        }
        if ((forWhom == null) || forWhom.isEmpty()) {
            throw new TagAPIException("Can't submit empty forWhom!");
        }
        for (final Player whom : forWhom) {
            if ((whom != null) && (!player.equals(whom))) {
                TagAPI.refreshPlayer(player, whom);
            }
        }
    }

    private static void check() {
        if (TagAPI.instance == null) {
            throw new TagAPIException("Can't fire TagAPI method while TagAPI is disabled!");
        }
        if (!Thread.currentThread().equals(TagAPI.mainThread)) {
            throw new TagAPIException("A plugin attempted to call a TagAPI method from another thread!");
        }
    }

    private boolean debug;
    private boolean wasEnabled;
    private HashMap<Integer, Player> entityIDMap;
    private IPacketHandler handler;

    @Override
    public Plugin getPlugin() {
        return this;
    }

    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onDisable()
     */
    @Override
    public void onDisable() {
        if (this.wasEnabled) {
            this.handler.shutdown();
        }
        TagAPI.instance = null;
        TagAPI.mainThread = null;
    }

    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {
        TagAPI.instance = this;
        this.entityIDMap = new HashMap<Integer, Player>();
        TagAPI.mainThread = Thread.currentThread();
        this.debug = this.getConfig().getBoolean("debug", false);
        if (this.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            this.getLogger().info("Detected ProtocolLib, using that for handling!");
            this.handler = new ProtocolLibHandler(this);
        } else {
            try {
                Class.forName("net.minecraft.server.Packet");
                this.handler = new org.kitteh.tag.compat.nms145pre.DefaultHandler(this);
            } catch (final ClassNotFoundException e) {
            }
            try {
                Class.forName("net.minecraft.server.v1_4_5.Packet");
                this.handler = new org.kitteh.tag.compat.nms145.DefaultHandler(this);
            } catch (final ClassNotFoundException e) {
            }
        }
        if (this.handler == null) {
            this.setEnabled(false);
        }
        if (!this.getServer().getPluginManager().isPluginEnabled(this)) {
            return;
        }

        this.handler.startup();
        this.wasEnabled = true;
        this.getServer().getPluginManager().registerEvents(new HeyListen(this), this);
        for (final Player player : this.getServer().getOnlinePlayers()) {
            this.in(player);
        }
        try {
            new MetricsLite(this).start();
        } catch (final IOException e) {
        }
    }

    @Override
    public void packet(Packet packet, Player destination) {
        if (TagAPI.instance == null) {
            throw new TagAPIException("TagAPI not loaded");
        }
        TagAPI.instance.handlePacket(packet, destination);
    }

    private void debug(String message) {
        if (this.debug) {
            this.getLogger().info(message);
        }
    }

    private void handlePacket(Packet packet, Player destination) {
        final Player named = this.entityIDMap.get(packet.entityId);
        if (named == null) {
            this.debug("Could not find entity ID " + packet.entityId + ". Discarded.");
            return;
        }
        if (destination == null) {
            this.debug("Encountered a packet destined for an unknown player. Discarded.");
            return;
        }
        final PlayerReceiveNameTagEvent event = new PlayerReceiveNameTagEvent(destination, named);
        this.getServer().getPluginManager().callEvent(event);
        if (event.isModified()) {
            String name = event.getTag();
            if (name.length() > 16) {
                name = name.substring(0, 16);
            }
            packet.tag = name;
        }
    }

    private void in(Player player) {
        this.entityIDMap.put(player.getEntityId(), player);
    }

}
