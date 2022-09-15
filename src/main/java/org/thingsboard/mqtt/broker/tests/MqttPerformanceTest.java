/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.config.TestRunConfiguration;
import org.thingsboard.mqtt.broker.data.ClientCredentialsType;
import org.thingsboard.mqtt.broker.data.Message;
import org.thingsboard.mqtt.broker.data.PersistentClientType;
import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.SubscriberAnalysisResult;
import org.thingsboard.mqtt.broker.data.SubscriberGroup;
import org.thingsboard.mqtt.broker.data.dto.MqttClientCredentialsDto;
import org.thingsboard.mqtt.broker.service.DummyClientService;
import org.thingsboard.mqtt.broker.service.PayloadGenerator;
import org.thingsboard.mqtt.broker.service.PersistedMqttClientService;
import org.thingsboard.mqtt.broker.service.PublishStats;
import org.thingsboard.mqtt.broker.service.PublisherService;
import org.thingsboard.mqtt.broker.service.SubscribeStats;
import org.thingsboard.mqtt.broker.service.SubscriberService;
import org.thingsboard.mqtt.broker.service.TbBrokerRestService;
import org.thingsboard.mqtt.broker.service.orchestration.ClusterSynchronizer;
import org.thingsboard.mqtt.broker.service.orchestration.TestRestService;
import org.thingsboard.mqtt.broker.util.ValidationUtil;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class MqttPerformanceTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    public static final String DEFAULT_USER_NAME = "default";

    private final DummyClientService dummyClientService;
    private final SubscriberService subscriberService;
    private final PublisherService publisherService;
    private final PersistedMqttClientService persistedMqttClientService;
    private final TestRunConfiguration testRunConfiguration;
    private final PayloadGenerator payloadGenerator;
    private final TestRestService testRestService;
    private final ClusterSynchronizer clusterSynchronizer;

    @Autowired(required = false)
    private TbBrokerRestService tbBrokerRestService;

    @PostConstruct
    public void init() throws Exception {
        ValidationUtil.validateSubscriberGroups(testRunConfiguration.getSubscribersConfig());
        ValidationUtil.validatePublisherGroups(testRunConfiguration.getPublishersConfig());
    }

    public void runTest() throws Exception {
        log.info("Start performance test.");

        printTestRunConfiguration();

        final UUID defaultCredentialsId = createDefaultMqttCredentials();

        persistedMqttClientService.clearPersistedSessions();
        persistedMqttClientService.removeApplicationClients();
        Thread.sleep(2000);
        persistedMqttClientService.initApplicationClients();

        SubscribeStats subscribeStats = new SubscribeStats(new DescriptiveStatistics(), new DescriptiveStatistics());

        subscriberService.connectSubscribers(subscribeStats);

        subscriberService.subscribe(subscribeStats);
        DescriptiveStatistics generalLatencyStats = subscribeStats.getLatencyStats();
        DescriptiveStatistics msgProcessingLatencyStats = subscribeStats.getMsgProcessingLatencyStats();

        dummyClientService.connectDummyClients();

        publisherService.connectPublishers();
        boolean orchestratorNotified = testRestService.notifyNodeIsReady();
        if (orchestratorNotified) {
            clusterSynchronizer.awaitClusterReady();
        }
        Thread.sleep(2000);
        publisherService.warmUpPublishers();
        Thread.sleep(1000);

        PublishStats publishStats = publisherService.startPublishing();


        Thread.sleep(TimeUnit.SECONDS.toMillis(testRunConfiguration.getSecondsToRun() + testRunConfiguration.getAdditionalSecondsToWait()));

        subscriberService.disconnectSubscribers();
        publisherService.disconnectPublishers();
        dummyClientService.disconnectDummyClients();

        // wait for all MQTT clients to close
        Thread.sleep(1000);

        persistedMqttClientService.clearPersistedSessions();

        dummyClientService.clearPersistedSessions();

        SubscriberAnalysisResult analysisResult = subscriberService.analyzeReceivedMessages();
        DescriptiveStatistics acknowledgedStats = publishStats.getPublishAcknowledgedStats();
        DescriptiveStatistics sentStats = publishStats.getPublishSentLatencyStats();

        log.info("Latency stats: median - {}, avg - {}, max - {}, min - {}, 95th - {}, lost messages - {}, duplicated messages - {}, total received messages - {}, " +
                        "publish sent messages - {}, publish sent latency median - {}, publish sent latency max - {}, " +
                        "publish acknowledged messages - {}, publish acknowledged latency median - {}, publish acknowledged latency max - {}, " +
                        "msg processing latency median - {}.",
                generalLatencyStats.getPercentile(50),
                generalLatencyStats.getMean(), generalLatencyStats.getMax(),
                generalLatencyStats.getMin(), generalLatencyStats.getPercentile(95),
                analysisResult.getLostMessages(), analysisResult.getDuplicatedMessages(),
                generalLatencyStats.getN(),
                sentStats.getN(), sentStats.getPercentile(50), sentStats.getMax(),
                acknowledgedStats.getN(), acknowledgedStats.getPercentile(50), acknowledgedStats.getMax(),
                msgProcessingLatencyStats.getPercentile(50)
        );

        publisherService.printDebugPublishersStats();
        subscriberService.printDebugSubscribersStats();

        // wait for all MQTT clients to close
        Thread.sleep(2000);
        persistedMqttClientService.removeApplicationClients();

        removeDefaultCredentials(defaultCredentialsId);

        log.info("Performance test finished.");
    }

    private UUID createDefaultMqttCredentials() {
        try {
            return UUID.fromString(tbBrokerRestService.createClientCredentials(
                    new MqttClientCredentialsDto(null, DEFAULT_USER_NAME, PersistentClientType.DEVICE, ClientCredentialsType.MQTT_BASIC)
            ));
        } catch (Exception e) {
            log.warn("Default credentials are already created!", e);
        }
        return null;
    }

    private void removeDefaultCredentials(UUID id) {
        if (id != null) {
            tbBrokerRestService.removeClientCredentials(id);
        }
    }

    private void printTestRunConfiguration() throws Exception {
        List<PublisherGroup> publisherGroups = testRunConfiguration.getPublishersConfig();
        List<SubscriberGroup> subscriberGroups = testRunConfiguration.getSubscribersConfig();
        int totalPublishers = publisherGroups.stream().mapToInt(PublisherGroup::getPublishers).sum();
        int nonPersistedSubscribers = subscriberGroups.stream()
                .filter(subscriberGroup -> subscriberGroup.getPersistentSessionInfo() == null)
                .mapToInt(SubscriberGroup::getSubscribers)
                .sum();
        int persistedApplicationsSubscribers = subscriberGroups.stream()
                .filter(subscriberGroup -> subscriberGroup.getPersistentSessionInfo() != null
                        && subscriberGroup.getPersistentSessionInfo().getClientType() == PersistentClientType.APPLICATION)
                .mapToInt(SubscriberGroup::getSubscribers)
                .sum();
        int persistedDevicesSubscribers = subscriberGroups.stream()
                .filter(subscriberGroup -> subscriberGroup.getPersistentSessionInfo() != null
                        && subscriberGroup.getPersistentSessionInfo().getClientType() == PersistentClientType.DEVICE)
                .mapToInt(SubscriberGroup::getSubscribers)
                .sum();
        int totalPublishedMessages = totalPublishers * testRunConfiguration.getTotalPublisherMessagesCount();
        int totalExpectedReceivedMessages = subscriberService.calculateTotalExpectedReceivedMessages();
        Message randomMsg = new Message(System.currentTimeMillis(), true, payloadGenerator.generatePayload());
        log.info("Test run info: publishers - {}, non-persistent subscribers - {}, regular persistent subscribers - {}, " +
                        "'APPLICATION' persistent subscribers - {}, dummy client connections - {}, " +
                        "publisher QoS - {}, subscriber QoS - {}, max messages per second - {}, " +
                        "run time - {}s, total published messages - {}, expected total received messages - {}, " +
                        "msg bytes size - {}",
                totalPublishers, nonPersistedSubscribers, persistedDevicesSubscribers,
                persistedApplicationsSubscribers, testRunConfiguration.getNumberOfDummyClients(),
                testRunConfiguration.getPublisherQoS(), testRunConfiguration.getSubscriberQoS(), testRunConfiguration.getMaxMessagesPerPublisherPerSecond(),
                testRunConfiguration.getSecondsToRun(), totalPublishedMessages, totalExpectedReceivedMessages,
                mapper.writeValueAsBytes(randomMsg).length);
    }
}
