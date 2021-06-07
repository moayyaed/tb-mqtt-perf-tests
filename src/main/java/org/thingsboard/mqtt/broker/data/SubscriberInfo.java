package org.thingsboard.mqtt.broker.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.thingsboard.mqtt.MqttClient;

import java.util.concurrent.atomic.AtomicInteger;

@AllArgsConstructor
@Getter
public class SubscriberInfo {
    private final MqttClient subscriber;
    private final int id;
    private final AtomicInteger receivedMsgs;
    private final SubscriberGroup subscriberGroup;
}