package org.thingsboard.mqtt.broker.util;

import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.SubscriberGroup;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class ValidationUtil {
    public static void validateSubscriberGroups(Collection<SubscriberGroup> subscriberGroups) {
        Set<Integer> distinctIds = subscriberGroups.stream().map(SubscriberGroup::getId).collect(Collectors.toSet());
        if (distinctIds.size() != subscriberGroups.size()) {
            throw new RuntimeException("Some subscriber IDs are equal");
        }
    }

    public static void validatePublisherGroups(Collection<PublisherGroup> publisherGroups) {
        Set<Integer> distinctIds = publisherGroups.stream().map(PublisherGroup::getId).collect(Collectors.toSet());
        if (distinctIds.size() != publisherGroups.size()) {
            throw new RuntimeException("Some publisher IDs are equal");
        }
    }

}
