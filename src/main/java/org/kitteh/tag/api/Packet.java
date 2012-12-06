package org.kitteh.tag.api;

public class Packet {
    public String tag;

    public int entityId;

    public Packet(String tag, int id) {
        this.tag = tag;
        this.entityId = id;
    }
}
