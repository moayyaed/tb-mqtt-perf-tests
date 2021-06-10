package org.thingsboard.mqtt.broker.data;

import lombok.Getter;

import java.util.Set;

@Getter
public class SubscriberGroup {
    private final int id;
    private final int subscribers;
    private final String topicFilter;
    private final Set<Integer> expectedPublisherGroups;
    private final PersistentSessionInfo persistentSessionInfo;
    private final String clientIdPrefix;

    public SubscriberGroup(int id, int subscribers, String topicFilter, Set<Integer> expectedPublisherGroups, PersistentSessionInfo persistentSessionInfo) {
        this(id, subscribers, topicFilter, expectedPublisherGroups, persistentSessionInfo, "test_sub_client_" + id + "_");
    }

    public SubscriberGroup(int id, int subscribers, String topicFilter, Set<Integer> expectedPublisherGroups, PersistentSessionInfo persistentSessionInfo, String clientIdPrefix) {
        this.id = id;
        this.subscribers = subscribers;
        this.topicFilter = topicFilter;
        this.expectedPublisherGroups = expectedPublisherGroups;
        this.persistentSessionInfo = persistentSessionInfo;
        this.clientIdPrefix = clientIdPrefix;
    }

    public String getClientId(int subscriberId) {
        return clientIdPrefix + subscriberId;
    }
}
