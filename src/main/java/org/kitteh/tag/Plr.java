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

import java.util.Map;
import java.util.WeakHashMap;

public class Plr {
    private final String name;
    private final Map<Plr, String> map = new WeakHashMap<Plr, String>();

    Plr(String name) {
        this.name = name;
    }

    String getName(Plr player) {
        return this.map.get(player);
    }

    void setName(Plr player, String name) {
        this.map.put(player, name);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Plr) {
            return ((Plr) o).name.equals(this.name);
        }
        return false;
    }
}