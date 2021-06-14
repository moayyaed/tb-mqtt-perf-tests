package org.thingsboard.mqtt.broker.service;

public interface PublisherService {
    void connectPublishers();

    void startPublishing();

    void disconnectPublishers();
}
