package org.thingsboard.mqtt.broker.service;


import org.thingsboard.mqtt.broker.data.SubscriberGroup;

import java.util.Collection;

public interface PersistedMqttClientService {
    void initApplicationClients(Collection<SubscriberGroup> subscriberGroups);

    void clearPersistedSessions(Collection<SubscriberGroup> subscriberGroups);

    void removeApplicationClients(Collection<SubscriberGroup> subscriberGroups);

}
