package org.thingsboard.mqtt.broker.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.thingsboard.mqtt.MqttClient;

@AllArgsConstructor
@Getter
public class PublisherInfo {
    private final MqttClient publisher;
    private final int id;
    private final String topic;
}