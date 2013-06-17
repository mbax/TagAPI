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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Team;
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
        public void onPlayerJoinLater(PlayerJoinEvent event) {
            for (Team team : event.getPlayer().getScoreboard().getTeams()) {
                Set<String> newNames = new HashSet<String>();
                for (OfflinePlayer player : team.getPlayers()) {
                    if (player instanceof Player) {
                        String newName = getName((Player) player, player.getName(), event.getPlayer());
                        if (!player.getName().equals(newName)) {
                            newNames.add(newName);
                        }
                    }
                }
                if (newNames.size() > 0) {
                    teamUpdates = newNames;
                    System.out.println("Storing names: " + newNames.toString() + " for team " + team.getName() + " to show to " + event.getPlayer().getName());
                    team.addPlayer(getServer().getOfflinePlayer(String.valueOf(ChatColor.COLOR_CHAR)));
                    System.out.println("Hurp");
                    teamUpdates = null;
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerQuit(PlayerQuitEvent event) {
            this.api.entityIDMap.remove(event.getPlayer().getEntityId());
            this.api.playerMap.remove(event.getPlayer().getName());
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
    private Map<Integer, Player> entityIDMap;
    private IPacketHandler handler;
    private Map<String, Plr> playerMap;
    private Set<String> teamUpdates = null;

    @Override
    public void debug(String message) {
        if (this.debug) {
            this.getLogger().info(message);
        }
    }

    @Override
    public String getNameForPacket20(final int entityID, final String initialName, final Player destination) {
        return this.call(new Callable<String>() {
            @Override
            public String call() throws Exception {
                final Player named = TagAPI.this.getPlayer(entityID);
                if (named != null) {
                    final Plr namedPlr = TagAPI.this.playerMap.get(named.getName());
                    final Plr destPlr = TagAPI.this.playerMap.get(destination.getName());
                    final String oldName = destPlr.getName(namedPlr);
                    final String newName = TagAPI.this.getName(named, initialName, destination);
                    if (!newName.equals(oldName)) {
                        // For now, not sending a score update, instead waiting on value to change once
                        for (final Team team : destination.getScoreboard().getTeams()) {
                            if (team.hasPlayer(named)) {
                                team.addPlayer(getServer().getOfflinePlayer(newName));
                            }
                        }
                    }
                    return newName;
                } else {
                    return initialName;
                }
            }
        });
    }

    @Override
    public String getNameForPacket207(final String playerName, final String objectiveName, final Player destination) {
        if (destination == null) {
            this.debug("Encountered a scoreboard packet destined for an unknown player. Discarded.");
            return playerName;
        }
        return this.call(new Callable<String>() {
            @Override
            public String call() throws Exception {
                final Objective objective = destination.getScoreboard().getObjective(objectiveName);
                if (objective == null) {
                    TagAPI.this.debug("Encountered an objective name that does not appear registered. Discarded.");
                    return playerName;
                }
                if (objective.getDisplaySlot() == DisplaySlot.BELOW_NAME) {
                    final Player named = TagAPI.this.getServer().getPlayerExact(playerName);
                    if (named != null) {
                        String newName = TagAPI.this.playerMap.get(destination.getName()).getName(TagAPI.this.playerMap.get(named.getName()));
                        if (newName == null) {
                            newName = TagAPI.this.getName(named, playerName, destination);
                        }
                        return newName;
                    }
                }
                return playerName;
            }

        });
    }

    @Override
    public Collection<?> getNamesForPacket209(final String teamName, final boolean deletion, final Collection<?> names, final Player destination) {
        if (names.size() == 0) {
            return names;
        }
        return this.call(new Callable<Collection<?>>() {
            @Override
            public Collection<?> call() throws Exception {
                final Set<String> newNames = new HashSet<String>();
                final Iterator<?> iterator = names.iterator();
                while (iterator.hasNext()) {
                    final String playerName = iterator.next().toString();
                    if (playerName.equals(String.valueOf(ChatColor.COLOR_CHAR))) {
                        if (teamUpdates != null) {
                            System.out.println("sending for " + destination.getName());
                            newNames.addAll(teamUpdates);
                        } else {
                            System.out.println("cancelling for " + destination.getName());
                            return null;
                        }
                    } else {
                        newNames.add(playerName);
                        final Player named = TagAPI.this.getServer().getPlayerExact(playerName);
                        if (named != null) {
                            String newName = TagAPI.this.playerMap.get(destination.getName()).getName(TagAPI.this.playerMap.get(named.getName()));
                            if (newName == null) {
                                newName = TagAPI.this.getName(named, playerName, destination);
                            }
                            newNames.add(newName);
                        }
                    }
                }
                /* There are too many... problems... with deletions
                 * Leaving this bug for now.
                if (deletion) { // Protect names still on the list
                    Set<String> keep = new HashSet<String>();
                    for (OfflinePlayer player : destination.getScoreboard().getTeam(teamName).getPlayers()) {
                        if (names.contains(player.getName())) {
                            continue;
                        }
                        String newName = TagAPI.this.playerMap.get(destination.getName()).getName(TagAPI.this.playerMap.get(player.getName()));
                        if (newName == null && player instanceof Player) {
                            newName = TagAPI.this.getName((Player) player, player.getName(), destination);
                        }
                        if(names.contains(player.getName()))
                        if (newName != null) {
                            keep.add(newName);
                        }
                        keep.add(player.getName());
                    }
                    newNames.removeAll(keep);
                    if (newNames.size() == 0) {
                        return null;
                    }
                }
                */
                return newNames;
            }
        });
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
        this.entityIDMap = new ConcurrentHashMap<Integer, Player>();
        this.playerMap = new ConcurrentHashMap<String, Plr>();
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
            final String cbversion = packageName.substring(packageName.lastIndexOf('.') + 1);
            try {
                final Class<?> clazz = Class.forName("org.kitteh.tag.compat." + cbversion + ".DefaultHandler");
                if (IPacketHandler.class.isAssignableFrom(clazz)) {
                    this.handler = (IPacketHandler) clazz.getConstructor(TagHandler.class).newInstance(this);
                }
            } catch (final Exception e) {
                this.getLogger().severe("Could not find support for this CraftBukkit version. Check for an update or pester mbaxter.");
                this.getLogger().info("Update hopefully available at http://dev.bukkit.org/server-mods/tag");
            }
            versionLoaded = cbversion;
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

    private String getName(Player named, String initialName, Player destination) {
        String playername = initialName;
        if (destination == null) {
            this.debug("Encountered a packet destined for an unknown player. Discarded.");
            return playername;
        }
        final PlayerReceiveNameTagEvent event = new PlayerReceiveNameTagEvent(destination, named, playername);
        if (!Thread.currentThread().equals(TagAPI.mainThread)) {
            Boolean ret = this.call(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    TagAPI.this.getServer().getPluginManager().callEvent(event);
                    return true;
                }
            });
            if (ret == null) {
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
        this.playerMap.get(destination.getName()).setName(this.playerMap.get(named.getName()), playername);
        return playername;
    }

    private <T> T call(Callable<T> call) {
        if (!Thread.currentThread().equals(TagAPI.mainThread)) {
            final Future<T> future = this.getServer().getScheduler().callSyncMethod(this, call);
            while (!future.isCancelled() && !future.isDone()) {
                try {
                    Thread.sleep(1);
                } catch (final InterruptedException e) {
                }
            }
            if (future.isCancelled()) {
                return null;
            }
            try {
                return future.get();
            } catch (InterruptedException e) {
            } catch (ExecutionException e) {
            }
            return null;
        }
        try {
            return call.call();
        } catch (Exception e) {
            return null;
        }
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
        this.playerMap.put(player.getName(), new Plr(player.getName()));
    }

}