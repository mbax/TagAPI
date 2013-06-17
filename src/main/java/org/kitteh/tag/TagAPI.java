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
package org.kitteh.tag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
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
            TagAPI.instance.debug("Found different thread " + Thread.currentThread().getName() + "! Expecting " + TagAPI.mainThread.getName());
            throw new TagAPIException("A plugin attempted to call a TagAPI method from another thread (" + Thread.currentThread().getName() + ")!");
        }
    }

    private boolean debug;
    private boolean wasEnabled;
    private HashMap<Integer, Player> entityIDMap;
    private IPacketHandler handler;
    private final Map<Integer, LivingEntity> entityTempMap = new HashMap<Integer, LivingEntity>();

    @Override
    public void debug(String message) {
        if (this.debug) {
            this.getLogger().info(message);
        }
    }

    @Override
    public EntityNameResult getNameForEntity(final int entityID, final Player destination) {
        final Callable<EntityNameResult> callable = new Callable<EntityNameResult>() {
            @Override
            public EntityNameResult call() throws Exception {
                final LivingEntity entity = TagAPI.this.getEntity(entityID, destination.getWorld());
                if (entity == null) {
                    // Debug removed because it picks up non-LivingEntity entities as null
                    //TagAPI.this.debug("Could not find Entity with ID " + entityID + ". Skipping.");
                    return null;
                }
                final String initialName = entity.getCustomName() != null ? entity.getCustomName() : "";
                final PlayerReceiveEntityNameTagEvent event = new PlayerReceiveEntityNameTagEvent(destination, entity, initialName, entity.isCustomNameVisible());
                TagAPI.this.getServer().getPluginManager().callEvent(event);
                return new EntityNameResult(event.getTag(), event.isTagVisible(), event.isTagModified(), event.isVisibleModified());
            }
        };
        if (!Thread.currentThread().equals(TagAPI.mainThread)) {
            final Future<EntityNameResult> future = this.getServer().getScheduler().callSyncMethod(this, callable);
            while (!future.isCancelled() && !future.isDone()) {
                try {
                    Thread.sleep(1);
                } catch (final InterruptedException e) {
                }
            }
            if (future.isCancelled()) {
                this.debug("Async task for tag of entity " + entityID + " to " + destination.getName() + " was cancelled.");
            }
            try {
                return future.get();
            } catch (final Exception e) {
            }
        } else {
            try {
                return callable.call();
            } catch (final Exception e) {
            }
        }
        return null;
    }

    @Override
    public String getNameForPacket20(int entityID, String initialName, Player destination) {
        final Player named = this.getPlayer(entityID);
        if (named != null) {
            return this.getName(named, initialName, destination);
        } else {
            return initialName;
        }
    }

    @Override
    public Plugin getPlugin() {
        return this;
    }

    @Override
    public void onDisable() {
        if (this.wasEnabled) {
            this.handler.shutdown();
        }
        TagAPI.instance = null;
        TagAPI.mainThread = null;
    }

    @Override
    public void onEnable() {
        TagAPI.instance = this;
        this.entityIDMap = new HashMap<Integer, Player>();
        TagAPI.mainThread = Thread.currentThread();
        this.debug = this.getConfig().getBoolean("debug", false);
        this.debug("Storing main thread: " + TagAPI.mainThread.getName());

        String versionLoaded;

        if (this.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            this.getLogger().info("Detected ProtocolLib, using that for handling!");
            this.handler = new ProtocolLibHandler(this);
            versionLoaded = "via ProtocolLib";
        } else {
            final String packageName = this.getServer().getClass().getPackage().getName();
            String cbversion = packageName.substring(packageName.lastIndexOf('.') + 1);
            if (cbversion.equals("craftbukkit")) {
                cbversion = "pre";
            }
            try {
                final Class<?> clazz = Class.forName("org.kitteh.tag.compat." + cbversion + ".DefaultHandler");
                if (IPacketHandler.class.isAssignableFrom(clazz)) {
                    this.handler = (IPacketHandler) clazz.getConstructor(TagHandler.class).newInstance(this);
                }
            } catch (final Exception e) {
                this.getLogger().severe("Could not find support for this CraftBukkit version. Check for an update or pester mbaxter.");
                this.getLogger().info("Update hopefully available at http://dev.bukkit.org/server-mods/tag");
            }
            versionLoaded = (cbversion.equals("pre") ? "1.4.5-pre-RB" : cbversion);
        }
        if (this.handler == null) {
            this.getServer().getPluginManager().disablePlugin(this);
        }
        if (!this.getServer().getPluginManager().isPluginEnabled(this)) {
            return;
        }

        this.getLogger().info("Using hooks for CraftBukkit " + versionLoaded);

        this.handler.startup();
        this.wasEnabled = true;

        this.getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                TagAPI.this.entityTempMap.clear();
            }
        }, 20, 20);

        this.getServer().getPluginManager().registerEvents(new HeyListen(this), this);
        for (final Player player : this.getServer().getOnlinePlayers()) {
            this.in(player);
        }

        try {
            new MetricsLite(this);
        } catch (final IOException e) {
            // Whatever!
        }
    }

    private LivingEntity getEntity(int entityID, World start) {
        LivingEntity entity = this.entityTempMap.get(entityID);
        if (entity != null) {
            return entity;
        }
        entity = this.getEntityFromWorld(entityID, start);
        if (entity == null) {
            for (final World world : this.getServer().getWorlds()) {
                if (world.equals(start)) {
                    continue;
                }
                if ((entity = this.getEntityFromWorld(entityID, world)) != null) {
                    break;
                }
            }
        }
        if (entity != null) {
            this.entityTempMap.put(entityID, entity);
        }
        return entity;
    }

    private LivingEntity getEntityFromWorld(int entityID, World world) {
        for (final Entity e : world.getEntities()) {
            if (e.getEntityId() == entityID) {
                return (LivingEntity) (e instanceof LivingEntity ? e : null);
            }
        }
        return null;
    }

    private String getName(Player named, String initialName, Player destination) {
        String playername = initialName;
        if (destination == null) {
            this.debug("Encountered a packet destined for an unknown player. Discarded.");
            return playername;
        }
        final PlayerReceiveNameTagEvent event = new PlayerReceiveNameTagEvent(destination, named, playername);
        if (!Thread.currentThread().equals(TagAPI.mainThread)) {
            final Future<Boolean> future = this.getServer().getScheduler().callSyncMethod(this, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    TagAPI.this.getServer().getPluginManager().callEvent(event);
                    return true;
                }
            });
            while (!future.isCancelled() && !future.isDone()) {
                try {
                    Thread.sleep(1);
                } catch (final InterruptedException e) {
                }
            }
            if (future.isCancelled()) {
                this.debug("Async task for tag of " + named.getName() + " to " + destination.getName() + " was cancelled. Skipping.");
                return playername;
            }
        } else {
            this.getServer().getPluginManager().callEvent(event);
        }
        String name = event.getTag();
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        playername = name;
        return playername;
    }

    private Player getPlayer(int entityId) {
        final Player named = this.entityIDMap.get(entityId);
        if (named == null) {
            this.debug("Could not find entity ID " + entityId + ". Discarded.");
            return null;
        }
        return named;
    }

    private void in(Player player) {
        this.entityIDMap.put(player.getEntityId(), player);
    }
}