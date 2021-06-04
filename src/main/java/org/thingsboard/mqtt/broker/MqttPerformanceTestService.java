package org.thingsboard.mqtt.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.broker.data.Message;
import org.thingsboard.mqtt.broker.data.PublisherInfo;
import org.thingsboard.mqtt.broker.data.SubscriberInfo;
import org.thingsboard.mqtt.broker.service.ClientInitializer;
import org.thingsboard.mqtt.broker.service.MockClientService;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttPerformanceTestService {
    private final ObjectMapper mapper = new ObjectMapper();


    private static final int SUBSCRIBERS = 5;
    private static final int PUBLISHERS = 5;
    private static final int MOCK_CLIENTS = 5;
    private static final int MSGS_TO_PUBLISH = 60;
    private static final int MAX_MSGS_PER_SECOND = 1;

    private static final String TOPIC_PREFIX = "europe/ua/kyiv/tb/";

    private final MockClientService mockClientService;
    private final ClientInitializer clientInitializer;

    @PostConstruct
    public void init() throws Exception {
        CountDownLatch subscribersCDL = new CountDownLatch(SUBSCRIBERS);
        List<SubscriberInfo> subscribers = new ArrayList<>(SUBSCRIBERS);

        DescriptiveStatistics generalLatencyStats = new DescriptiveStatistics();

        log.info("Start connecting subscribers.");
        for (int i = 0; i < SUBSCRIBERS; i++) {
            int subscriberId = i;
            MqttClient subClient = clientInitializer.initClient("test_sub_client_" + subscriberId);
            AtomicInteger receivedMsgs = new AtomicInteger(0);
            subscribers.add(new SubscriberInfo(subClient, subscriberId, receivedMsgs));
            String topicFilter = subscriberId % 2 == 0 ? TOPIC_PREFIX + "+" : TOPIC_PREFIX + "#";

            AtomicBoolean successfullySubscribed = new AtomicBoolean(false);
            subClient.on(topicFilter, (topic, mqttMessageByteBuf) -> {
                try {
                    long now = System.currentTimeMillis();
                    byte[] mqttMessageBytes = toBytes(mqttMessageByteBuf);
                    Message message = mapper.readValue(mqttMessageBytes, Message.class);
                    generalLatencyStats.addValue(now - message.getCreateTime());
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
            MqttClient pubClient = clientInitializer.initClient("test_pub_client_" + i);
            String topic = TOPIC_PREFIX + i;
            publishers.add(new PublisherInfo(pubClient, i, topic));
        }
        log.info("Finished connecting publishers.");

        mockClientService.connectMockClients(MOCK_CLIENTS);

        AtomicInteger publishedMsgsPerPublisher = new AtomicInteger();
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            if (publishedMsgsPerPublisher.getAndIncrement() >= MSGS_TO_PUBLISH) {
                return;
            }
            for (PublisherInfo publisherInfo : publishers) {
                try {
                    Message message = new Message(System.currentTimeMillis());
                    byte[] messageBytes = mapper.writeValueAsBytes(message);
                    publisherInfo.getPublisher().publish(publisherInfo.getTopic(), toByteBuf(messageBytes), MqttQoS.AT_MOST_ONCE)
                            .addListener(future -> {
                                        if (!future.isSuccess()) {
                                            log.error("[{}] Error publishing msg", publisherInfo.getId());
                                        }
                                    }
                            );
                } catch (Exception e) {
                    log.error("[{}] Failed to publish", publisherInfo.getId(), e);
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
                subscriberInfo.getSubscriber().disconnect();
            } catch (Exception e) {
                log.error("[{}] Failed to disconnect subscriber", subscriberInfo.getId());
            }
        }
        for (SubscriberInfo subscriberInfo : subscribers) {
            if (subscriberInfo.getReceivedMsgs().get() != PUBLISHERS * MSGS_TO_PUBLISH) {
                log.error("[{}] Subscriber received {} messages instead of {}",
                        subscriberInfo.getId(), subscriberInfo.getReceivedMsgs().get(), PUBLISHERS * MSGS_TO_PUBLISH);
            }
        }

        log.info("Disconnecting publishers.");
        for (PublisherInfo publisherInfo : publishers) {
            try {
                publisherInfo.getPublisher().disconnect();
            } catch (Exception e) {
                log.error("[{}] Failed to disconnect publisher", publisherInfo.getId());
            }
        }

        mockClientService.disconnectMockClients();

        log.info("Latency stats: avg - {}, median - {}, max - {}, min - {}, 95th - {}, total received msgs - {}.",
                generalLatencyStats.getSum() / generalLatencyStats.getN(),
                generalLatencyStats.getMean(), generalLatencyStats.getMax(),
                generalLatencyStats.getMin(), generalLatencyStats.getPercentile(95),
                generalLatencyStats.getN());
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
}
