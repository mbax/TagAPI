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

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

public class ExampleTagAPIPlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onNameTag(PlayerReceiveNameTagEvent event) {
        // Here are some examples, to be considered separately as they may overlap

        /* 
         * First example. 
         * Every player I see will be Notch
         */
        if (event.getPlayer().getName().equals("mbaxter")) { // mbaxter is the user RECEIVING the nametag packet
            event.setTag("Notch"); // Set the tag for this event to Notch
        }

        /*
         * Second example.
         * If nobody else has touched the event yet, make everyone's name ChatColor.MAGIC
         */
        if (!event.isModified()) { // Event untouched!
            event.setTag(ChatColor.MAGIC + event.getNamedPlayer().getName()); // Add the magic "color" to everyone's name!
        }

        /*
         * Third example.
         * If the player is vanished (VanishNoPacket) make their name blue
         * This is pretty much what I do in VanishNoPacket
         */
        for (final MetadataValue value : event.getNamedPlayer().getMetadata("vanished")) {
            // If this metadata is vanishnopacket -owned 
            //  and the value (value.asBoolean()) is 'true'
            if (value.getOwningPlugin().getName().equals("VanishNoPacket") && value.asBoolean()) { // If they're vanished
                event.setTag(ChatColor.BLUE + event.getNamedPlayer().getName());
                //I can do this because only players who can see vanished players will get sent this
            }
        }
    }

}
