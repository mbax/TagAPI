package org.kitteh.tag.api;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public interface TagHandler {

    public void debug(String message);

    public String packet(int entityId, String playername, Player destination);

    public Plugin getPlugin();

}