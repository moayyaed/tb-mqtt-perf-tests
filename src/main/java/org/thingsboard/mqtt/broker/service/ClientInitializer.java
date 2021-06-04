package org.thingsboard.mqtt.broker.service;

import org.thingsboard.mqtt.MqttClient;

public interface ClientInitializer {
    MqttClient initClient(String clientId);
}
