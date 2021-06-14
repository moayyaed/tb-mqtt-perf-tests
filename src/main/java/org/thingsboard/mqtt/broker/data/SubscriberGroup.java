package org.thingsboard.mqtt.broker.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
        this(id, subscribers, topicFilter, expectedPublisherGroups, persistentSessionInfo, null);
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public SubscriberGroup(@JsonProperty("id") int id, @JsonProperty("subscribers") int subscribers, @JsonProperty("topicFilter") String topicFilter,
                           @JsonProperty("expectedPublisherGroups") Set<Integer> expectedPublisherGroups,
                           @JsonProperty("persistentSessionInfo") PersistentSessionInfo persistentSessionInfo,
                           @JsonProperty("clientIdPrefix") String clientIdPrefix) {
        this.id = id;
        this.subscribers = subscribers;
        this.topicFilter = topicFilter;
        this.expectedPublisherGroups = expectedPublisherGroups;
        this.persistentSessionInfo = persistentSessionInfo;
        this.clientIdPrefix = clientIdPrefix != null ? clientIdPrefix : "test_sub_client_" + id + "_";
    }

    public String getClientId(int subscriberId) {
        return clientIdPrefix + subscriberId;
    }
}
