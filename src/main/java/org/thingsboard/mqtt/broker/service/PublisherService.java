package org.thingsboard.mqtt.broker.service;

import io.netty.handler.codec.mqtt.MqttQoS;
import org.thingsboard.mqtt.broker.data.PublisherGroup;

import java.util.Collection;

public interface PublisherService {
    void connectPublishers(Collection<PublisherGroup> publisherGroups);

    void startPublishing(int totalProducerMessagesCount, int maxMessagesPerProducerPerSecond, MqttQoS qos);

    void disconnectPublishers();
}
