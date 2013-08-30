/*
 * Copyright 2012-2013 Matt Baxter
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

import org.apache.commons.lang.Validate;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Here is where the magic is made.
 * 
 * Catch this event in order to have an effect on the player's name tag
 */
public class PlayerReceiveNameTagEvent extends PlayerEvent {

    private static final HandlerList handlers = new HandlerList();

    /**
     * This is a Bukkit method. Don't touch me.
     * 
     * @return registered handlers to Bukkit
     */
    public static HandlerList getHandlerList() {
        return PlayerReceiveNameTagEvent.handlers;
    }

    private boolean modified;
    private final Player named;
    private String tag;

    /**
     * TagAPI creates all the event objects for you.
     * 
     * @param who
     *            The player receiving the nametag info
     * @param named
     *            The player whose nametag we're talking about
     * @deprecated
     */
    @Deprecated
    public PlayerReceiveNameTagEvent(Player who, Player named) {
        super(who);
        Validate.notNull(who, "Destination player cannot be null!");
        Validate.notNull(named, "Named player cannot be null!");
        this.modified = false;
        this.named = named;
        this.tag = named.getName();
    }

    /**
     * TagAPI creates all the event objects for you.
     * 
     * @param who
     *            The player receiving the nametag info
     * @param named
     *            The player whose nametag we're talking about
     * @param initialName
     *            Initial name tag
     */
    public PlayerReceiveNameTagEvent(Player who, Player named, String initialName) {
        super(who);
        Validate.notNull(who, "Destination player cannot be null!");
        Validate.notNull(named, "Named player cannot be null!");
        Validate.notNull(initialName, "Initial player name cannot be null!");
        this.modified = named.getName().equals(initialName);
        this.named = named;
        this.tag = initialName;
    }

    @Override
    public HandlerList getHandlers() {
        return PlayerReceiveNameTagEvent.handlers;
    }

    /**
     * Get the player whose nametag we're receiving
     * 
     * @return the Player whose name is being affected
     */
    public Player getNamedPlayer() {
        return this.named;
    }

    /**
     * Get the nametag that will be sent
     * 
     * @return String nametag that will be sent
     */
    public String getTag() {
        return this.tag;
    }

    /**
     * Has the event been modified yet?
     * 
     * Excellent method for plugins wishing to be rather passive
     * 
     * @return true if the event has had the tag modified
     */
    public boolean isModified() {
        return this.modified;
    }

    /**
     * Set the nametag. Will always set the name tag whether returning true or false.
     * 
     * @param tag
     *            The desired tag. Only 16 chars accepted. The rest will be truncated.
     * @return true if accepted as-is, false if it was too long and was truncated.
     */
    public boolean setTag(String tag) {
        Validate.notNull(tag, "New nametag cannot be null!");
        if (this.tag.equals(tag)) {
            return true;
        }
        this.tag = tag;
        this.modified = true;
        if (tag.length() > 16) {
            tag = tag.substring(0, 16);
            return false;
        }
        return true;
    }

}