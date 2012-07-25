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

import org.apache.commons.lang.Validate;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class PlayerReceiveNameTagEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlerList() {
        return PlayerReceiveNameTagEvent.handlers;
    }

    private boolean modified;
    private final Player named;
    private String tag;

    public PlayerReceiveNameTagEvent(Player who, Player named) {
        super(who);
        this.modified = false;
        this.named = named;
        this.tag = named.getName();
    }

    @Override
    public HandlerList getHandlers() {
        return PlayerReceiveNameTagEvent.handlers;
    }

    public Player getNamedPlayer() {
        return this.named;
    }

    public String getTag() {
        return this.tag;
    }

    public boolean isModified() {
        return this.modified;
    }

    public boolean setTag(String tag) {
        Validate.notNull(tag, "New nametag cannot be null!");
        this.tag = tag;
        this.modified = true;
        if (tag.length() > 16) {
            tag = tag.substring(0, 16);
            return false;
        }
        return true;
    }
}
