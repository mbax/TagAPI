package org.kitteh.tag.handler;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.kitteh.tag.TagAPI;
import org.kitteh.tag.TagAPIException;

import net.minecraft.server.NetworkManager;
import net.minecraft.server.Packet20NamedEntitySpawn;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class DefaultHandler implements PacketHandler {
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
    private Field syncField;
    private Field highField;

    private final TagAPI plugin;

    public DefaultHandler(TagAPI plugin) {
        this.plugin = plugin;
        try {
            this.syncField = NetworkManager.class.getDeclaredField("h");
            this.syncField.setAccessible(true);
            this.highField = NetworkManager.class.getDeclaredField("highPriorityQueue");
            this.highField.setAccessible(true);
        } catch (final Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to enable. Check for TagAPI updates.");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    @Override
    public void in(Player player) {
        try {
            this.nom(this.getManager(player), Collections.synchronizedList(new ArrayLizt(player, this.plugin)));
        } catch (final Exception e) {
            new TagAPIException("[TagAPI] Failed to inject into networkmanager for " + player.getName(), e).printStackTrace();
        }
    }

    @Override
    public void out(Player player) {
        try {
            this.nom(this.getManager(player), Collections.synchronizedList(new ArrayList()), true);
        } catch (final Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to restore " + player.getName() + ". Could be a problem.", e);
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
