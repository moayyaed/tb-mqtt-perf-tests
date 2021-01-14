package org.thingsboard.mqtt.broker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class MqttPerformanceTestService {
    private final ObjectMapper mapper = new ObjectMapper();

    private static final int SUBSCRIBERS = 5;
    private static final int PUBLISHERS = 20;
    private static final int MSGS_TO_PUBLISH = 1000;
    private static final int MAX_MSGS_PER_SECOND = 100;

    private static final String TOPIC_PREFIX = "europe/ua/kyiv/tb/";

    private static final String TEST_MESSAGE = "test_message";


    @Value("${mqtt.host}")
    private String mqttHost;
    @Value("${mqtt.port}")
    private int mqttPort;

    @PostConstruct
    public void init() throws Exception {
        CountDownLatch subscribersCDL = new CountDownLatch(SUBSCRIBERS);
        List<SubscriberInfo> subscribers = new ArrayList<>(SUBSCRIBERS);

        DescriptiveStatistics generalLatencyStats = new DescriptiveStatistics();

        log.info("Start connecting subscribers.");
        for (int i = 0; i < SUBSCRIBERS; i++) {
            int subscriberId = i;
            MqttClient subClient = new MqttClient("tcp://" + mqttHost + ":" + mqttPort, "test_sub_client_" + i, new MemoryPersistence());
            subClient.connect();
            AtomicInteger receivedMsgs = new AtomicInteger(0);
            subscribers.add(new SubscriberInfo(subClient, subscriberId, receivedMsgs));
            String topicFilter = subscriberId % 2 == 0 ? TOPIC_PREFIX + "+" : TOPIC_PREFIX + "#";

            subClient.subscribe(topicFilter, (topic, mqttMessage) -> {
                try {
                    Message message = mapper.readValue(mqttMessage.getPayload(), Message.class);
                    long now = System.currentTimeMillis();
                    generalLatencyStats.addValue(now - message.createTime);
                    if (receivedMsgs.incrementAndGet() >= PUBLISHERS * MSGS_TO_PUBLISH) {
                        subscribersCDL.countDown();
                    }
                } catch (Exception e) {
                    log.error("[{}] Failed to process msg {}", subscriberId, mqttMessage);
                }
            });
        }
        log.info("Finished connecting subscribers.");

        log.info("Start connecting publishers.");
        List<PublisherInfo> publishers = new ArrayList<>(PUBLISHERS);
        for (int i = 0; i < PUBLISHERS; i++) {
            MqttAsyncClient pubAsyncClient = new MqttAsyncClient("tcp://" + mqttHost + ":" + mqttPort, "test_pub_client_" + i,
                    new MemoryPersistence());
            pubAsyncClient.connect().waitForCompletion();
            String topic = TOPIC_PREFIX + i;
            publishers.add(new PublisherInfo(pubAsyncClient, i, topic));
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
                    MqttMessage mqttMessage = new MqttMessage(mapper.writeValueAsBytes(message));
                    publisherInfo.publisher.publish(publisherInfo.topic, mqttMessage);
                } catch (MqttException | JsonProcessingException e) {
                    log.error("[{}] Failed to publish", publisherInfo.id, e);
                }
            }
        }, 0, 1000 / MAX_MSGS_PER_SECOND, TimeUnit.MILLISECONDS);

        boolean successfullyProcessed = subscribersCDL.await(MSGS_TO_PUBLISH / MAX_MSGS_PER_SECOND + 5, TimeUnit.SECONDS);
        if (!successfullyProcessed) {
            log.error("Timeout waiting for subscribers to process messages.");
            for (SubscriberInfo subscriberInfo : subscribers) {
                if (subscriberInfo.receivedMsgs.get() != PUBLISHERS * MSGS_TO_PUBLISH) {
                    log.error("[{}] Subscriber received only {} messages", subscriberInfo.id, subscriberInfo.receivedMsgs.get());
                }
            }
        }
        log.info("Disconnecting subscribers.");
        for (SubscriberInfo subscriberInfo : subscribers) {
            try {
                subscriberInfo.subscriber.disconnect();
            } catch (MqttException e) {
                log.error("[{}] Failed to disconnect subscriber", subscriberInfo.id);
            }
        }

        log.info("Disconnecting publishers.");
        for (PublisherInfo publisherInfo : publishers) {
            try {
                publisherInfo.publisher.disconnect();
            } catch (MqttException e) {
                log.error("[{}] Failed to disconnect publisher", publisherInfo.id);
            }
        }

        log.info("Latency stats: avg - {}, median - {}, max - {}, min - {}, 95th - {}.",
                generalLatencyStats.getSum() / generalLatencyStats.getN(),
                generalLatencyStats.getMean(), generalLatencyStats.getMax(),
                generalLatencyStats.getMin(), generalLatencyStats.getPercentile(95));
    }

    @AllArgsConstructor
    @Getter
    private static class PublisherInfo {
        private final MqttAsyncClient publisher;
        private final int id;
        private final String topic;
    }

    @AllArgsConstructor
    @Getter
    private static class SubscriberInfo {
        private final MqttClient subscriber;
        private final int id;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    private static class Message {
        private long createTime;
    }
}
