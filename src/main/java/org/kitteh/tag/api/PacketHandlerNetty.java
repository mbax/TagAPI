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
package org.kitteh.tag.api;

import java.lang.reflect.Field;

import net.minecraft.util.io.netty.channel.Channel;
import net.minecraft.util.io.netty.channel.ChannelHandlerContext;
import net.minecraft.util.io.netty.channel.ChannelOutboundHandlerAdapter;
import net.minecraft.util.io.netty.channel.ChannelPipeline;
import net.minecraft.util.io.netty.channel.ChannelPromise;

import org.bukkit.entity.Player;

public abstract class PacketHandlerNetty extends PacketHandlerBase {

    /**
     * Must be public to remove from netty pipeline
     */
    public class TagAPIChannelOutboundHandler extends ChannelOutboundHandlerAdapter {

        private final Player player;

        private TagAPIChannelOutboundHandler(Player player) {
            this.player = player;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) throws Exception {
            try {
                PacketHandlerNetty.this.handlePacket(packet, this.player);
            } catch (final Exception e) {
                // TODO: Panic
            }
            super.write(ctx, packet, promise);
        }

    }

    public PacketHandlerNetty(TagHandler handler) throws PacketHandlerException {
        super(handler);
    }

    protected ChannelPipeline getPipeline(Player player) {
        Channel channel;
        try {
            channel = (Channel) this.getChannelField().get(this.getNetworkManager(player));
        } catch (final Exception e) {
            e.printStackTrace();
            return null;
        }
        return channel.pipeline();
    }

    @Override
    protected void hookPlayer(Player player) {
        final ChannelPipeline pipeline = this.getPipeline(player);
        if (pipeline != null) {
            pipeline.addLast(new TagAPIChannelOutboundHandler(player));
        }
    }

    @Override
    protected void releasePlayer(Player player) {
        final ChannelPipeline pipeline = this.getPipeline(player);
        if ((pipeline != null) && (pipeline.get(TagAPIChannelOutboundHandler.class) != null)) {
            this.getPipeline(player).remove(TagAPIChannelOutboundHandler.class);
        }
    }

    protected abstract Field getChannelField();

    protected abstract Object getNetworkManager(Player player);

    protected abstract void handlePacket(Object packet, Player destination) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException;

}
