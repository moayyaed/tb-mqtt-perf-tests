package org.thingsboard.mqtt.broker.service;

public interface MockClientService {
    void connectMockClients(int numberOfClients);

    void disconnectMockClients();
}
