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
package org.thingsboard.mqtt.broker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.client.mqtt.MqttClient;
import org.thingsboard.mqtt.broker.config.TestRunConfiguration;
import org.thingsboard.mqtt.broker.data.dto.MqttClientDto;
import org.thingsboard.mqtt.broker.data.PersistentClientType;
import org.thingsboard.mqtt.broker.data.SubscriberGroup;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersistedMqttClientServiceImpl implements PersistedMqttClientService {
    @Autowired(required = false)
    private TbBrokerRestService tbBrokerRestService;

    private final ClientInitializer clientInitializer;
    private final TestRunConfiguration testRunConfiguration;

    @Override
    public void initApplicationClients() {
        if (tbBrokerRestService == null) {
            return;
        }

        List<SubscriberGroup> subscriberGroups = testRunConfiguration.getSubscribersConfig();
        List<SubscriberGroup> applicationSubscriberGroups = subscriberGroups.stream()
                .filter(subscriberGroup -> subscriberGroup.getPersistentSessionInfo() != null)
                .filter(subscriberGroup -> subscriberGroup.getPersistentSessionInfo().getClientType() == PersistentClientType.APPLICATION)
                .collect(Collectors.toList());
        if (applicationSubscriberGroups.isEmpty()) {
            return;
        }

        log.info("Initializing {} {} subscribers.", applicationSubscriberGroups.stream().mapToInt(SubscriberGroup::getSubscribers).sum(), PersistentClientType.APPLICATION);
        for (SubscriberGroup subscriberGroup : applicationSubscriberGroups) {
            for (int i = 0; i < subscriberGroup.getSubscribers(); i++) {
                String clientId = subscriberGroup.getClientId(i);
                MqttClientDto client = tbBrokerRestService.getClient(clientId);
                if (client == null) {
                    tbBrokerRestService.createClient(new MqttClientDto(clientId, clientId, PersistentClientType.APPLICATION));
                } else if (client.getType() == PersistentClientType.DEVICE){
                    log.error("Client with ID {} exists with {} client type.", client, PersistentClientType.DEVICE);
                    throw new RuntimeException("Client with ID " + clientId + " exists with " + PersistentClientType.DEVICE + " type");
                }
            }
        }
    }

    @Override
    public void clearPersistedSessions() {
        List<SubscriberGroup> subscriberGroups = testRunConfiguration.getSubscribersConfig();
        List<SubscriberGroup> persistedSubscriberGroups = subscriberGroups.stream()
                .filter(subscriberGroup -> subscriberGroup.getPersistentSessionInfo() != null)
                .collect(Collectors.toList());

        log.info("Clearing persisted session for {} subscriber groups", persistedSubscriberGroups.size());

        for (SubscriberGroup persistedSubscriberGroup : persistedSubscriberGroups) {
            for (int i = 0; i < persistedSubscriberGroup.getSubscribers(); i++) {
                String clientId = persistedSubscriberGroup.getClientId(i);
                try {
                    MqttClient mqttClient = clientInitializer.initClient(clientId, true);
                    mqttClient.disconnect();
                } catch (Exception e) {
                    log.warn("[{}] Failed to clear persisted session", clientId, e);
                }
            }
        }
    }

    @Override
    public void removeApplicationClients() {
        if (tbBrokerRestService == null) {
            return;
        }

        List<SubscriberGroup> subscriberGroups = testRunConfiguration.getSubscribersConfig();
        List<SubscriberGroup> applicationSubscriberGroups = subscriberGroups.stream()
                .filter(subscriberGroup -> subscriberGroup.getPersistentSessionInfo() != null)
                .filter(subscriberGroup -> subscriberGroup.getPersistentSessionInfo().getClientType() == PersistentClientType.APPLICATION)
                .collect(Collectors.toList());
        if (applicationSubscriberGroups.isEmpty()) {
            return;
        }

        log.info("Removing {} {} subscribers.", applicationSubscriberGroups.stream().mapToInt(SubscriberGroup::getSubscribers).sum(), PersistentClientType.APPLICATION);
        for (SubscriberGroup subscriberGroup : applicationSubscriberGroups) {
            for (int i = 0; i < subscriberGroup.getSubscribers(); i++) {
                String clientId = subscriberGroup.getClientId(i);
                try {
                    tbBrokerRestService.removeClient(clientId);
                } catch (Exception e) {
                    log.warn("[{}] Failed to remove {} client", clientId, PersistentClientType.APPLICATION);
                }
            }
        }
    }
}
