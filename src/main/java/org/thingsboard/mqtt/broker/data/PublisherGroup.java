package org.thingsboard.mqtt.broker.data;

import lombok.Getter;

@Getter
public class PublisherGroup {
    private final int id;
    private final int publishers;
    private final String topicPrefix;
    private final String clientIdPrefix;

    public PublisherGroup(int id, int publishers, String topicPrefix) {
        this(id, publishers, topicPrefix, "test_pub_client_" + id + "_");
    }

    public PublisherGroup(int id, int publishers, String topicPrefix, String clientIdPrefix) {
        this.id = id;
        this.publishers = publishers;
        this.topicPrefix = topicPrefix;
        this.clientIdPrefix = clientIdPrefix;
    }

    public String getClientId(int publisherId) {
        return clientIdPrefix + publisherId;
    }

}
