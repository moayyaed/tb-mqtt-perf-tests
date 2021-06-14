package org.thingsboard.mqtt.broker.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class PublisherGroup {
    private final int id;
    private final int publishers;
    private final String topicPrefix;
    private final String clientIdPrefix;

    public PublisherGroup(int id, int publishers, String topicPrefix) {
        this(id, publishers, topicPrefix, null);
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public PublisherGroup(@JsonProperty("id") int id, @JsonProperty("publishers") int publishers,
                          @JsonProperty("topicPrefix") String topicPrefix, @JsonProperty("clientIdPrefix") String clientIdPrefix) {
        this.id = id;
        this.publishers = publishers;
        this.topicPrefix = topicPrefix;
        this.clientIdPrefix = clientIdPrefix != null ? clientIdPrefix : "test_pub_client_" + id + "_";
    }

    public String getClientId(int publisherId) {
        return clientIdPrefix + publisherId;
    }

}
