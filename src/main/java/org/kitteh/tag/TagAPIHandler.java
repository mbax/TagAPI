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

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.kitteh.tag.api.TagHandler;
import org.kitteh.tag.api.TagInfo;

class TagAPIHandler implements TagHandler {

    private TagAPI plugin;

    TagAPIHandler(TagAPI plugin) {
        this.plugin = plugin;
    }

    @Override
    public void debug(String message) {
        this.plugin.debug(message);
    }

    @Override
    public TagInfo getNameForPacket20(String initialUUID, int entityID, String initialName, Player destination) {
        return this.plugin.getNameForPacket20(initialUUID, entityID, initialName, destination);
    }

    @Override
    public Plugin getPlugin() {
        return this.plugin;
    }

}