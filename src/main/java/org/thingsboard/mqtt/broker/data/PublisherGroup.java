package org.thingsboard.mqtt.broker.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PublisherGroup {
    private final int id;
    private final int publishers;
    private final String topicPrefix;
}
