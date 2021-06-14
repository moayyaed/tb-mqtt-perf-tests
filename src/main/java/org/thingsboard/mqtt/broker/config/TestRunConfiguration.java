package org.thingsboard.mqtt.broker.config;

import io.netty.handler.codec.mqtt.MqttQoS;
import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.SubscriberGroup;

import java.util.List;

public interface TestRunConfiguration {
    String getConfigurationName();

    List<SubscriberGroup> getSubscribersConfig();

    List<PublisherGroup> getPublishersConfig();

    int getMaxMessagesPerPublisherPerSecond();

    int getSecondsToRun();

    int getAdditionalSecondsToWait();

    int getTotalPublisherMessagesCount();

    int getNumberOfDummyClients();

    MqttQoS getPublisherQoS();

    MqttQoS getSubscriberQoS();
}
