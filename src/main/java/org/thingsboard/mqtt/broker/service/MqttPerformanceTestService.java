package org.thingsboard.mqtt.broker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.concurrent.Future;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.MqttClientConfig;
import org.thingsboard.mqtt.MqttConnectResult;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class MqttPerformanceTestService {
    private final ObjectMapper mapper = new ObjectMapper();
    private EventLoopGroup EVENT_LOOP_GROUP;

    private static final int CONNECT_TIMEOUT = 5;

    private static final int SUBSCRIBERS = 1000;
    private static final int PUBLISHERS = 5;
    private static final int MSGS_TO_PUBLISH = 20;
    private static final int MAX_MSGS_PER_SECOND = 1;

    private static final String TOPIC_PREFIX = "europe/ua/kyiv/tb/";


    @Value("${mqtt.host}")
    private String mqttHost;
    @Value("${mqtt.port}")
    private int mqttPort;

    @PostConstruct
    public void init() throws Exception {
        EVENT_LOOP_GROUP = new NioEventLoopGroup();
        CountDownLatch subscribersCDL = new CountDownLatch(SUBSCRIBERS);
        List<SubscriberInfo> subscribers = new ArrayList<>(SUBSCRIBERS);

        DescriptiveStatistics generalLatencyStats = new DescriptiveStatistics();

        log.info("Start connecting subscribers.");
        for (int i = 0; i < SUBSCRIBERS; i++) {
            int subscriberId = i;
            MqttClient subClient = initClient("test_sub_client_" + subscriberId);
            AtomicInteger receivedMsgs = new AtomicInteger(0);
            subscribers.add(new SubscriberInfo(subClient, subscriberId, receivedMsgs));
            String topicFilter = subscriberId % 2 == 0 ? TOPIC_PREFIX + "+" : TOPIC_PREFIX + "#";

            AtomicBoolean successfullySubscribed = new AtomicBoolean(false);
            subClient.on(topicFilter, (topic, mqttMessageByteBuf) -> {
                try {
                    long now = System.currentTimeMillis();
                    byte[] mqttMessageBytes = toBytes(mqttMessageByteBuf);
                    Message message = mapper.readValue(mqttMessageBytes, Message.class);
                    generalLatencyStats.addValue(now - message.createTime);
                    if (receivedMsgs.incrementAndGet() == PUBLISHERS * MSGS_TO_PUBLISH) {
                        subscribersCDL.countDown();
                    }
                } catch (Exception e) {
                    log.error("[{}] Failed to process msg", subscriberId);
                }
            }).addListener(future -> {
                if (successfullySubscribed.getAndSet(true)) {
                    log.warn("[{}] Subscribed to topic more than one time!", subscriberId);
                }
            });
        }
        log.info("Finished connecting subscribers.");

        log.info("Start connecting publishers.");
        List<PublisherInfo> publishers = new ArrayList<>(PUBLISHERS);
        for (int i = 0; i < PUBLISHERS; i++) {
            MqttClient pubClient = initClient("test_pub_client_" + i);
            String topic = TOPIC_PREFIX + i;
            publishers.add(new PublisherInfo(pubClient, i, topic));
        }
        log.info("Finished connecting publishers.");

        AtomicInteger publishedMsgsPerPublisher = new AtomicInteger();
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            if (publishedMsgsPerPublisher.getAndIncrement() >= MSGS_TO_PUBLISH) {
                return;
            }
            for (PublisherInfo publisherInfo : publishers) {
                try {
                    Message message = new Message(System.currentTimeMillis());
                    byte[] messageBytes = mapper.writeValueAsBytes(message);
                    publisherInfo.publisher.publish(publisherInfo.topic, toByteBuf(messageBytes), MqttQoS.AT_MOST_ONCE)
                            .addListener(future -> {
                                        if (!future.isSuccess()) {
                                            log.error("[{}] Error publishing msg", publisherInfo.id);
                                        }
                                    }
                            );
                } catch (Exception e) {
                    log.error("[{}] Failed to publish", publisherInfo.id, e);
                }
            }
        }, 0, 1000 / MAX_MSGS_PER_SECOND, TimeUnit.MILLISECONDS);

        boolean successfullyProcessed = subscribersCDL.await(MSGS_TO_PUBLISH / MAX_MSGS_PER_SECOND + 5, TimeUnit.SECONDS);
        if (!successfullyProcessed) {
            log.error("Timeout waiting for subscribers to process messages.");
        }
        log.info("Disconnecting subscribers.");
        for (SubscriberInfo subscriberInfo : subscribers) {
            try {
                subscriberInfo.subscriber.disconnect();
            } catch (Exception e) {
                log.error("[{}] Failed to disconnect subscriber", subscriberInfo.id);
            }
        }
        for (SubscriberInfo subscriberInfo : subscribers) {
            if (subscriberInfo.receivedMsgs.get() != PUBLISHERS * MSGS_TO_PUBLISH) {
                log.error("[{}] Subscriber received {} messages instead of {}",
                        subscriberInfo.id, subscriberInfo.receivedMsgs.get(), PUBLISHERS * MSGS_TO_PUBLISH);
            }
        }

        log.info("Disconnecting publishers.");
        for (PublisherInfo publisherInfo : publishers) {
            try {
                publisherInfo.publisher.disconnect();
            } catch (Exception e) {
                log.error("[{}] Failed to disconnect publisher", publisherInfo.id);
            }
        }

        log.info("Latency stats: avg - {}, median - {}, max - {}, min - {}, 95th - {}, total received msgs - {}.",
                generalLatencyStats.getSum() / generalLatencyStats.getN(),
                generalLatencyStats.getMean(), generalLatencyStats.getMax(),
                generalLatencyStats.getMin(), generalLatencyStats.getPercentile(95),
                generalLatencyStats.getN());
    }


    private MqttClient initClient(String clientId) throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(clientId);
        MqttClient subClient = MqttClient.create(config, null);
        subClient.setEventLoop(EVENT_LOOP_GROUP);
        Future<MqttConnectResult> connectFuture = subClient.connect(mqttHost, mqttPort);
        MqttConnectResult result;
        try {
            result = connectFuture.get(CONNECT_TIMEOUT, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            connectFuture.cancel(true);
            subClient.disconnect();
            throw new RuntimeException(String.format("Failed to connect to MQTT broker at %s:%d with client %s.",
                    mqttHost, mqttPort, clientId));
        }
        if (!result.isSuccess()) {
            connectFuture.cancel(true);
            subClient.disconnect();
            throw new RuntimeException(String.format("Failed to connect to MQTT broker at %s:%d with client %s. Result code is: %s",
                    mqttHost, mqttPort, clientId, result.getReturnCode()));
        }
        return subClient;
    }

    private static byte[] toBytes(ByteBuf inbound) {
        byte[] bytes = new byte[inbound.readableBytes()];
        int readerIndex = inbound.readerIndex();
        inbound.getBytes(readerIndex, bytes);
        return bytes;
    }

    private static final ByteBufAllocator ALLOCATOR = new UnpooledByteBufAllocator(false);

    private static ByteBuf toByteBuf(byte[] bytes) {
        ByteBuf payload = ALLOCATOR.buffer();
        payload.writeBytes(bytes);
        return payload;
    }

    @PreDestroy
    public void destroy() {
        if (!EVENT_LOOP_GROUP.isShutdown()) {
            EVENT_LOOP_GROUP.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
    }

    @AllArgsConstructor
    @Getter
    private static class PublisherInfo {
        private final MqttClient publisher;
        private final int id;
        private final String topic;
    }

    @AllArgsConstructor
    @Getter
    private static class SubscriberInfo {
        private final MqttClient subscriber;
        private final int id;
        private final AtomicInteger receivedMsgs;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    private static class Message {
        private long createTime;
    }
}
