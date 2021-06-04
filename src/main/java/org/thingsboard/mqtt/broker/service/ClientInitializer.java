package org.thingsboard.mqtt.broker.service;

import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.MqttHandler;

public interface ClientInitializer {
    MqttClient initClient(String clientId);

    MqttClient initClient(String clientId, boolean cleanSession);

    MqttClient initClient(String clientId, boolean cleanSession, MqttHandler defaultHandler);
}
