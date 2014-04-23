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

import net.minecraft.util.io.netty.channel.Channel;
import net.minecraft.util.io.netty.channel.ChannelHandlerContext;
import net.minecraft.util.io.netty.channel.ChannelOutboundHandlerAdapter;
import net.minecraft.util.io.netty.channel.ChannelPipeline;
import net.minecraft.util.io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class PacketHandlerNetty extends PacketHandlerBase {

    private class DisposalCrew extends Thread {
        private int count = 0;
        private boolean finished = false;
        private final Queue<ChannelPipeline> junkHeap = new ConcurrentLinkedQueue<ChannelPipeline>();

        @Override
        public void run() {
            while (!this.finished || !this.junkHeap.isEmpty()) {
                long destination = System.currentTimeMillis() + 3333; // THREE
                while (this.junkHeap.isEmpty()) {
                    if (System.currentTimeMillis() > destination) {
                        System.out.println("TagAPI just failed miserably to clean up.. Report this to mbaxter: [Remaining: " + this.junkHeap.size() + "+, Total:" + this.count + "]");
                        return;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                clean();
            }
        }

        private void clean() {
            ChannelPipeline trash;
            while ((trash = this.junkHeap.poll()) != null) {
                trash.remove(TagAPIChannelOutboundHandler.class);
            }
        }

        private void dispose(ChannelPipeline pipeline) {
            this.junkHeap.add(pipeline);
            this.count++;
        }

        private void finished() {
            this.finished = true;
        }
    }

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

    private DisposalCrew disposalCrew;

    public PacketHandlerNetty(TagHandler handler) throws PacketHandlerException {
        super(handler);
        Field field = this.getChannelField();
        if (!Channel.class.isAssignableFrom(field.getType())) {
            throw new PacketHandlerException(field.getDeclaringClass().getSimpleName() + "'s " + field.getName() + " field is not of type Channel");
        }
    }

    private void dispose(ChannelPipeline pipeline) {
        if (this.disposalCrew == null) {
            this.disposalCrew = new DisposalCrew();
            this.disposalCrew.start();
        }
        this.disposalCrew.dispose(pipeline);
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
            this.dispose(pipeline);
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (this.disposalCrew != null) {
            this.disposalCrew.finished();
        }
    }

    protected abstract Field getChannelField();

    protected abstract Object getNetworkManager(Player player);

    protected abstract void handlePacket(Object packet, Player destination) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException;

}
