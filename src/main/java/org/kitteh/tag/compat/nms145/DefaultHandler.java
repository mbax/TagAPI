package org.kitteh.tag.compat.nms145;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.craftbukkit.v1_4_5.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.kitteh.tag.api.PacketHandler;
import org.kitteh.tag.api.TagHandler;
import org.kitteh.tag.api.Packet;
import org.kitteh.tag.api.TagAPIException;

import net.minecraft.server.v1_4_5.NetworkManager;
import net.minecraft.server.v1_4_5.Packet20NamedEntitySpawn;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class DefaultHandler implements PacketHandler {
    public class ArrayLizt extends ArrayList {

        private static final long serialVersionUID = 2L;

        private final Player owner;

        private final TagHandler api;

        public ArrayLizt(Player owner, TagHandler api) {
            this.owner = owner;
            this.api = api;
        }

        @Override
        public boolean add(Object o) {
            if (o instanceof Packet20NamedEntitySpawn) {
                try {
                    final Packet20NamedEntitySpawn packet = ((Packet20NamedEntitySpawn) o);
                    final Packet p = new Packet(packet.b, packet.a);
                    this.api.packet(p, this.owner);
                    packet.b = p.tag;
                } catch (final Exception e) {
                    // Just in case!
                }
            }
            return super.add(o);
        }
    }

    @SuppressWarnings("unused")
    private class HandlerListener implements Listener {

        private final DefaultHandler handler;

        public HandlerListener(DefaultHandler handler) {
            this.handler = handler;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerJoin(PlayerJoinEvent event) {
            this.handler.in(event.getPlayer());
        }
    }

    private Field syncField;
    private Field highField;

    private final Plugin plugin;
    private final TagHandler handler;

    public DefaultHandler(TagHandler handler) {
        this.plugin = handler.getPlugin();
        this.handler = handler;
        try {
            this.syncField = NetworkManager.class.getDeclaredField("h");
            this.syncField.setAccessible(true);
            this.highField = NetworkManager.class.getDeclaredField("highPriorityQueue");
            this.highField.setAccessible(true);
        } catch (final Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to enable. Check for TagAPI updates.");
            this.plugin.getServer().getPluginManager().disablePlugin(this.plugin);
            return;
        }
        this.plugin.getServer().getPluginManager().registerEvents(new HandlerListener(this), this.plugin);
    }

    public void in(Player player) {
        try {
            this.nom(this.getManager(player), Collections.synchronizedList(new ArrayLizt(player, this.handler)));
        } catch (final Exception e) {
            new TagAPIException("[TagAPI] Failed to inject into networkmanager for " + player.getName(), e).printStackTrace();
        }
    }

    public void out(Player player) {
        try {
            this.nom(this.getManager(player), Collections.synchronizedList(new ArrayList()), true);
        } catch (final Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to restore " + player.getName() + ". Could be a problem.", e);
        }
    }

    @Override
    public void shutdown() {
        for (final Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (player != null) {
                this.out(player);
            }
        }
    }

    private NetworkManager getManager(Player player) {
        return (NetworkManager) ((CraftPlayer) player).getHandle().netServerHandler.networkManager;
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
}
