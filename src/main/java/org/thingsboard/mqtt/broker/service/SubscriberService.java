package org.thingsboard.mqtt.broker.service;

import io.netty.handler.codec.mqtt.MqttQoS;
import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.SubscriberAnalysisResult;
import org.thingsboard.mqtt.broker.data.SubscriberGroup;

import java.util.Collection;

public interface SubscriberService {
    void startSubscribers(Collection<SubscriberGroup> subscriberGroups, MqttQoS qos, MqttMsgProcessor msgProcessor);

    void disconnectSubscribers();

    SubscriberAnalysisResult analyzeReceivedMessages(Collection<PublisherGroup> publisherGroups, int totalProducerMessagesCount);

    int calculateTotalExpectedReceivedMessages(Collection<SubscriberGroup> subscriberGroups, Collection<PublisherGroup> publisherGroups, int totalProducerMessagesCount);
}
