package org.thingsboard.mqtt.broker.service;

import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.SubscriberGroup;

import java.util.Collection;

public interface PublisherService {
    void connectPublishers(Collection<PublisherGroup> publisherGroups);

    void startPublishing(int totalProducerMessagesCount, int maxMessagesPerProducerPerSecond);

    void disconnectPublishers();
}
