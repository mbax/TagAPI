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
package org.kitteh.tag;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.kitteh.tag.api.IPacketHandler;
import org.kitteh.tag.api.PacketHandlerNetty;
import org.kitteh.tag.api.TagAPIException;
import org.kitteh.tag.api.TagHandler;
import org.kitteh.tag.api.TagInfo;
import org.kitteh.tag.handler.ProtocolLibHandler;
import org.kitteh.tag.metrics.MetricsLite;

public class TagAPI extends JavaPlugin {

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
    private static final int tickPeriod = 5;
    private static final Pattern UUID_FIXER = Pattern.compile("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})");

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
    private long eventWait;
    private boolean wasEnabled;
    private HashMap<Integer, Player> entityIDMap;
    private IPacketHandler handler;
    private TagHandler tagHandler;

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
        this.tagHandler = new TagAPIHandler(this);
        this.entityIDMap = new HashMap<Integer, Player>();
        TagAPI.mainThread = Thread.currentThread();
        this.saveDefaultConfig();
        this.debug = this.getConfig().getBoolean("debug", false);
        if (this.getConfig().contains("asyncTickCheckPeriod")) {
            this.saveResource("config.yml", true);
            this.reloadConfig();
        }
        this.eventWait = this.getConfig().getLong("eventwait");
        this.debug("Storing main thread: " + TagAPI.mainThread.getName());

        final String impName = this.getServer().getName();
        if (impName.equals("CraftBukkit")) {
            final String impVersion = this.getServer().getVersion();
            if (!impVersion.startsWith("git-Bukkit")) {
                this.getLogger().warning("Inconsistency found: Potential mod detected. TagAPI may not run properly. Let's try it anyway!");
                this.getLogger().info("It looks like you're using a mod, but it's claiming to be \"CraftBukkit\" when I ask it.");
                this.getLogger().info("When I check the version string I get \"" + impVersion + "\" which doesn't sound like CraftBukkit to me (Could be wrong!).");
                // this.getLogger().info("One part of the Bukkit API is a method called getName, which should reply the implementation (mod) name.");
                // this.getLogger().info("If you are running a mod, you should request that the mod author update their implementation name to their mod.");
                // this.getLogger().info("Having this method return accurate results is useful for informative purposes. :)");
                this.getLogger().info("The above (or below, if your log reads in reverse) message DOES NOT mean there is a problem with TagAPI. Don't panic. Just breathe!");
            }
        } else {
            this.getLogger().warning("Mod detected: \"" + impName + "\". TagAPI may not run properly. Let's try it anyway!");
            this.getLogger().info("The above (or below, if your log reads in reverse) message DOES NOT mean there is a problem with TagAPI. Don't panic. Just breathe!");
        }

        String versionLoaded;
        Throwable exception = null;

        final String packageName = this.getServer().getClass().getPackage().getName();
        String cbversion = packageName.substring(packageName.lastIndexOf('.') + 1);
        if (cbversion.equals("craftbukkit")) {
            cbversion = "pre";
        }
        try {
            final Class<?> clazz = Class.forName("org.kitteh.tag.compat." + cbversion + ".DefaultHandler");
            if (IPacketHandler.class.isAssignableFrom(clazz)) {
                this.handler = (IPacketHandler) clazz.getConstructor(TagHandler.class).newInstance(this.tagHandler);
            }
        } catch (final Exception e) {
            if (e instanceof InvocationTargetException) {
                exception = e.getCause();
            }
        }
        versionLoaded = (cbversion.equals("pre") ? "1.4.5-pre-RB" : cbversion);

        if (((this.handler == null) || !(this.handler instanceof PacketHandlerNetty)) && this.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            this.getLogger().info("Detected ProtocolLib, using that for handling!");
            this.handler = new ProtocolLibHandler(this.tagHandler);
            versionLoaded = "via ProtocolLib";
        }

        if (this.handler == null) {
            this.getLogger().severe("Could not find support for this " + this.getServer().getName() + " version (" + cbversion + "). Check for an update or pester mbaxter.");
            this.getLogger().info("Update hopefully available at http://dev.bukkit.org/server-mods/tag");
            if (exception != null) {
                this.getLogger().log(Level.INFO, "Cause of the failure", exception);
            }
            this.getServer().getPluginManager().disablePlugin(this);
        }
        if (!this.getServer().getPluginManager().isPluginEnabled(this)) {
            return;
        }

        this.getLogger().info("Using hooks for CraftBukkit " + versionLoaded);

        this.handler.startup();
        this.wasEnabled = true;

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

    private TagInfo getName(UUID initialUUID, Player named, String initialName, Player destination) {
        if (destination == null) {
            this.debug("Encountered a packet destined for an unknown player. Discarded.");
            return null;
        }
        final AsyncPlayerReceiveNameTagEvent event = new AsyncPlayerReceiveNameTagEvent(destination, named, initialName, initialUUID);
        this.handleDeprecatedEvent(event);
        this.getServer().getPluginManager().callEvent(event);
        String name = event.getTag();
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        return new TagInfo(event.getUUID(), name);
    }

    private Player getPlayer(int entityId) {
        final Player named = this.entityIDMap.get(entityId);
        if (named == null) {
            this.debug("Could not find entity ID " + entityId + ". Discarded.");
            return null;
        }
        return named;
    }

    @SuppressWarnings("deprecation")
    private void handleDeprecatedEvent(AsyncPlayerReceiveNameTagEvent aEvent) {
        if (PlayerReceiveNameTagEvent.getHandlerList().getRegisteredListeners().length == 0) {
            return;
        }
        final PlayerReceiveNameTagEvent event = new PlayerReceiveNameTagEvent(aEvent.getPlayer(), aEvent.getNamedPlayer(), aEvent.getTag());
        if (!Thread.currentThread().equals(TagAPI.mainThread)) {
            final Future<Boolean> future = this.getServer().getScheduler().callSyncMethod(this, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    TagAPI.this.getServer().getPluginManager().callEvent(event);
                    return true;
                }
            });
            final long start = System.currentTimeMillis();
            while (!future.isCancelled() && !future.isDone()) {
                if ((TagAPI.mainThread == null) || ((System.currentTimeMillis() - start) > this.eventWait)) {
                    if (TagAPI.mainThread != null) {
                        this.getLogger().severe("Event took too long (limit: " + this.eventWait + "ms). Ignoring for " + aEvent.getNamedPlayer().getName() + " as seen by " + aEvent.getPlayer().getName());
                    }
                    return;
                }
                try {
                    Thread.sleep(TagAPI.tickPeriod);
                } catch (final InterruptedException ex) {
                }
            }
            if (future.isCancelled()) {
                this.debug("Sync task for tag of " + aEvent.getNamedPlayer().getName() + " to " + aEvent.getPlayer().getName() + " was cancelled. Skipping.");
                return;
            }
        } else {
            this.getServer().getPluginManager().callEvent(event);
        }
        aEvent.setTag(event.getTag());
    }

    private void in(Player player) {
        this.entityIDMap.put(player.getEntityId(), player);
    }

    void debug(String message) {
        if (this.debug) {
            this.getLogger().info(message);
        }
    }

    TagInfo getNameForPacket20(String initialUUID, int entityID, String initialName, Player destination) {
        final Player named = this.getPlayer(entityID);
        if (named != null) {
            UUID uuid = null;
            if (initialUUID != null) {
                final String uuidString = TagAPI.UUID_FIXER.matcher(initialUUID).replaceFirst("$1-$2-$3-$4-$5");
                try {
                    uuid = UUID.fromString(uuidString);
                } catch (final IllegalArgumentException e) {
                }
            }
            if (uuid == null) {
                uuid = named.getUniqueId();
            }
            return this.getName(uuid, named, initialName, destination);
        } else {
            return null;
        }
    }

}