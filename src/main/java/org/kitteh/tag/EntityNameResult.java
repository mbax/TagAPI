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

public class EntityNameResult {
    private final String tag;
    private final boolean visible;
    private final boolean tagModified;
    private final boolean visibleModified;

    EntityNameResult(String tag, boolean visible, boolean tagModified, boolean visibleModified) {
        this.tag = tag;
        this.visible = visible;
        this.tagModified = tagModified;
        this.visibleModified = visibleModified;
    }

    public String getTag() {
        return this.tag;
    }

    public boolean isTagModified() {
        return this.tagModified;
    }

    public boolean isTagVisible() {
        return this.visible;
    }

    public boolean isVisibleModified() {
        return this.visibleModified;
    }
}