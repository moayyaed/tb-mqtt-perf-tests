package org.thingsboard.mqtt.broker.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class MqttPerformanceTestService {
    private static final int SUBSCRIBERS = 20;
    private static final int PUBLISHERS = 300;
    private static final int MSGS_TO_PUBLISH = 200;
    private static final int MSGS_PER_SECOND = 20;

    private static final String TOPIC_PREFIX = "europe/ua/kyiv/tb/";

    private static final String TEST_MESSAGE = "test_message";


    @Value("${mqtt.host}")
    private String mqttHost;
    @Value("${mqtt.port}")
    private int mqttPort;

    @PostConstruct
    public void init() throws Exception {
        CountDownLatch subscribersCDL = new CountDownLatch(SUBSCRIBERS);
        for (int i = 0; i < SUBSCRIBERS; i++) {
            MqttClient subClient = new MqttClient("tcp://" + mqttHost + ":" + mqttPort, "test_sub_client_" + i);
            subClient.connect();
            String topicFilter = i % 2 == 0 ? TOPIC_PREFIX + "+" : TOPIC_PREFIX + "#";
            AtomicInteger receivedMsgs = new AtomicInteger(0);
            subClient.subscribe(topicFilter, (topic, message) -> {
                if (receivedMsgs.incrementAndGet() >= PUBLISHERS * MSGS_TO_PUBLISH) {
                    subClient.disconnect();
                    subClient.close();
                    subscribersCDL.countDown();
                }
            });
        }

        List<PublisherInfo> publishers = new ArrayList<>(PUBLISHERS);
        for (int i = 0; i < PUBLISHERS; i++) {
            MqttAsyncClient pubAsyncClient = new MqttAsyncClient("tcp://" + mqttHost + ":" + mqttPort, "test_pub_client_" + i);
            pubAsyncClient.connect();
            String topic = TOPIC_PREFIX + i;
            publishers.add(new PublisherInfo(pubAsyncClient, i, topic));
        }
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            for (PublisherInfo publisherInfo : publishers) {
                try {
                    publisherInfo.publisher.publish(publisherInfo.topic, new MqttMessage(TEST_MESSAGE.getBytes()));
                } catch (MqttException e) {
                    log.error("[{}] Failed to publish", publisherInfo.id);
                }
            }
        }, 0, 1000 / MSGS_PER_SECOND, TimeUnit.MILLISECONDS);

        subscribersCDL.await(MSGS_TO_PUBLISH / MSGS_PER_SECOND + 5, TimeUnit.SECONDS);
        for (PublisherInfo publisherInfo : publishers) {
            try {
                publisherInfo.publisher.disconnect();
            } catch (MqttException e) {
                log.error("[{}] Failed to disconnect", publisherInfo.id);
            }
        }
    }

    @AllArgsConstructor
    @Getter
    private static class PublisherInfo {
        private final MqttAsyncClient publisher;
        private final int id;
        private final String topic;
    }
}
