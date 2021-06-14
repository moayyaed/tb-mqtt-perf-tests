package org.thingsboard.mqtt.broker.service;


public interface PersistedMqttClientService {
    void initApplicationClients();

    void clearPersistedSessions();

    void removeApplicationClients();

}
