package org.thingsboard.mqtt.broker.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.SubscriberGroup;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestRunConfigurationInfo {
    private List<PublisherGroup> publisherGroups;
    private List<SubscriberGroup> subscriberGroups;
    private int dummyClients;
    private int secondsToRun;
    private int additionalSecondsToWait;
    private int maxMsgsPerPublisherPerSecond;
    private int publisherQosValue;
    private int subscriberQosValue;
}
