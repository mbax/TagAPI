package org.kitteh.tag.api;

import java.util.ArrayList;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public abstract class PacketHandler implements IPacketHandler {
    public class ArrayLizt<E> extends ArrayList<E> {

        private static final long serialVersionUID = 2L;

        private final Player owner;

        public ArrayLizt(Player owner) {
            this.owner = owner;
        }

        @Override
        public boolean add(E o) {
            PacketHandler.this.handlePacketAdd(o, this.owner);
            return super.add(o);
        }
    }

    protected abstract void handlePacketAdd(Object o, Player owner);

    public class HandlerListener implements Listener {

        private final PacketHandler handler;

        public HandlerListener(PacketHandler handler) {
            this.handler = handler;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerJoin(PlayerJoinEvent event) {
            this.handler.hookPlayer(event.getPlayer());
        }
    }

    protected final Plugin plugin;
    protected final TagHandler handler;

    public PacketHandler(TagHandler handler) {
        this.plugin = handler.getPlugin();
        this.handler = handler;
        this.plugin.getServer().getPluginManager().registerEvents(new HandlerListener(this), this.plugin);
    }

    protected abstract void hookPlayer(Player player);

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

    protected abstract void releasePlayer(Player player);
}
