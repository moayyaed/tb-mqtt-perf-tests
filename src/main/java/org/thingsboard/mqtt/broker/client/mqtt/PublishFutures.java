package org.thingsboard.mqtt.broker.client.mqtt;

import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PublishFutures {
    private final ChannelFuture publishSentFuture;
    private final Future<Void> publishFinishedFuture;
}
