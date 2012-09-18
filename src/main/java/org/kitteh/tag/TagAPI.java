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
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;

import net.minecraft.server.*;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.kitteh.tag.metrics.MetricsLite;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class TagAPI extends JavaPlugin {

    public class ArrayLizt extends ArrayList {

        private static final long serialVersionUID = 2L;

        private final Player owner;

        private final TagAPI api;

        public ArrayLizt(Player owner, TagAPI api) {
            this.owner = owner;
            this.api = api;
        }

        @Override
        public boolean add(Object o) {
            if (o instanceof Packet20NamedEntitySpawn) {
                try {
                    this.api.packet(((Packet20NamedEntitySpawn) o), this.owner);
                } catch (final Exception e) {
                    // Just in case!
                }
            }
            return super.add(o);
        }
    }

    @SuppressWarnings("unused")
    private class HeyListen implements Listener {

        private final TagAPI api;

        public HeyListen(TagAPI api) {
            this.api = api;
        }

        @EventHandler(priority = EventPriority.MONITOR)
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
    private Field syncField;
    private Field highField;
    private HashMap<Integer, EntityPlayer> entityIDMap;

    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onDisable()
     */
    @Override
    public void onDisable() {
        if (this.wasEnabled) {
            for (final Player player : this.getServer().getOnlinePlayers()) {
                if (player != null) {
                    try {
                        this.nom(this.getManager(player), Collections.synchronizedList(new ArrayList()), true);
                    } catch (final Exception e) {
                        this.getLogger().log(Level.WARNING, "Failed to restore " + player.getName() + ". Could be a problem.", e);
                    }
                }
            }
        }
        TagAPI.instance = null;
        TagAPI.mainThread = null;
    }

    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {
        TagAPI.mainThread = Thread.currentThread();
        this.getServer().getPluginManager().registerEvents(new HeyListen(this), this);
        this.entityIDMap = new HashMap<Integer, EntityPlayer>();
        TagAPI.instance = this;
        try {
            this.syncField = NetworkManager.class.getDeclaredField("h");
            this.syncField.setAccessible(true);
            this.highField = NetworkManager.class.getDeclaredField("highPriorityQueue");
            this.highField.setAccessible(true);
        } catch (final Exception e) {
            this.getLogger().log(Level.SEVERE, "Failed to enable. Check for TagAPI updates.");
            this.getServer().getPluginManager().disablePlugin(this);
        }
        this.wasEnabled = true;
        for (final Player player : this.getServer().getOnlinePlayers()) {
            this.in(player);
        }
        this.debug = this.getConfig().getBoolean("debug", false);
        try {
            new MetricsLite(this).start();
        } catch (final IOException e) {
        }
    }

    private void debug(String message) {
        if (this.debug) {
            this.getLogger().info(message);
        }
    }

    private NetworkManager getManager(Player player) {
        return (NetworkManager) ((CraftPlayer) player).getHandle().netServerHandler.networkManager;
    }

    private void handlePacket(Packet20NamedEntitySpawn packet, Player destination) {
        final EntityPlayer entity = this.entityIDMap.get(packet.a);
        if (entity == null) {
            this.debug("Encountered a packet with an unknown entityID. Discarded. ID " + packet.a);
            return;
        }
        final Player named = entity.getBukkitEntity();
        if (named == null) {
            this.debug("Player " + entity.name + " seems to have violated laws of spacetime. Discarded.");
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
            packet.b = name;
        }
    }

    private void in(Player player) {
        this.entityIDMap.put(player.getEntityId(), ((CraftPlayer) player).getHandle());
        try {
            this.nom(this.getManager(player), Collections.synchronizedList(new ArrayLizt(player, this)));
        } catch (final Exception e) {
            new TagAPIException("[TagAPI] Failed to inject into networkmanager for " + player.getName(), e).printStackTrace();
        }
    }

    private void nom(NetworkManager nm, List list) throws IllegalArgumentException, IllegalAccessException {
        this.nom(nm, list, false);
    }

    private void nom(NetworkManager nm, List list, boolean onlyIfOldIsHacked) throws IllegalArgumentException, IllegalAccessException {
        final List old = (List) this.highField.get(nm);
        if (onlyIfOldIsHacked) {
            if (!(old instanceof ArrayLizt)) {
                return;
            }
        }
        synchronized (this.syncField.get(nm)) {
            for (final Object object : old) {
                list.add(object);
            }
            this.highField.set(nm, list);
        }
    }

    private void packet(Packet20NamedEntitySpawn packet, Player destination) {
        if (TagAPI.instance == null) {
            throw new TagAPIException("TagAPI not loaded");
        }
        TagAPI.instance.handlePacket(packet, destination);
    }
}
