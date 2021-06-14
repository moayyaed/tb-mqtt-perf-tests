package org.thingsboard.mqtt.broker.tests;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
public class CreateTopicsTest {
    private static final int TOPICS = 200_000;
    private static final int SUBSCRIBERS = 100;
    private static final int MAX_LEVELS = 6;
    private static final int MAX_SEGMENT_SIZE = 5;

    private static final int PAUSE = 30_000;


    @Value("${mqtt.host}")
    private String mqttHost;
    @Value("${mqtt.port}")
    private int mqttPort;

    private final Random r = new Random();

    @PostConstruct
    public void init() throws Exception {
        int topicsPerSubscriber = TOPICS / SUBSCRIBERS;

        List<MqttClient> subscribers = new ArrayList<>();

        log.info("Start connecting subscribers.");
        for (int i = 0; i < SUBSCRIBERS; i++) {
            MqttClient subClient = new MqttClient("tcp://" + mqttHost + ":" + mqttPort, "test_sub_client_" + i, new MemoryPersistence());
            subClient.connect();
            subscribers.add(subClient);

            String[] topicFilters = new String[topicsPerSubscriber];
            for (int j = 0; j < topicsPerSubscriber; j++) {
                topicFilters[j] = generateTopicFilter();
            }
            subClient.subscribe(topicFilters);
        }
        log.info("Finished connecting subscribers.");

        Thread.sleep(PAUSE);

        log.info("Disconnecting subscribers.");
        for (MqttClient subClient : subscribers) {
            try {
                subClient.disconnect();
            } catch (Exception e) {
                log.error("Failed to disconnect subscriber");
            }
        }
    }

    private String generateTopicFilter() {
        int topicLength = r.nextInt(MAX_LEVELS) + 1;
        StringBuilder topicBuilder = new StringBuilder();
        for (int i = 0; i < topicLength; i++) {
            String topicSegment = UUID.randomUUID().toString().substring(0, r.nextInt(MAX_SEGMENT_SIZE) + 1);
            topicBuilder.append(topicSegment);
            if (i != topicLength - 1) {
                topicBuilder.append("/");
            }
        }
        return topicBuilder.toString();
    }
}
