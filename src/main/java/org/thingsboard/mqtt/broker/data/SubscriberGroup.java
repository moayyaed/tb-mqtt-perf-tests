/**
 * Copyright © 2016-2021 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
