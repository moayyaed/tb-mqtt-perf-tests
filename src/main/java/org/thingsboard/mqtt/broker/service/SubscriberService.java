package org.thingsboard.mqtt.broker.service;

import org.thingsboard.mqtt.broker.data.SubscriberAnalysisResult;

public interface SubscriberService {
    void startSubscribers(MqttMsgProcessor msgProcessor);

    void disconnectSubscribers();

    SubscriberAnalysisResult analyzeReceivedMessages();

    int calculateTotalExpectedReceivedMessages();
}
