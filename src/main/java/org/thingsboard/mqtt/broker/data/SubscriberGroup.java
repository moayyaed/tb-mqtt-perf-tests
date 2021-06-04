package org.thingsboard.mqtt.broker.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

@Getter
@AllArgsConstructor
public class SubscriberGroup {
    private final int id;
    private final int subscribers;
    private final String topicFilter;
    private final Set<Integer> expectedPublisherGroups;
    private final PersistentSessionInfo persistentSessionInfo;


    public String getClientId(int subscriberId) {
        return "test_sub_client_" + id + "_" + subscriberId;
    }
}
