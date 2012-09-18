package org.kitteh.tag.handler;

import org.bukkit.entity.Player;

public interface PacketHandler {
    public void in(Player player);

    public void out(Player player);
}
