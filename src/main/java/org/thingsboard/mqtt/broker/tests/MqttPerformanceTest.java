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
package org.thingsboard.mqtt.broker.tests;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.config.TestRunConfiguration;
import org.thingsboard.mqtt.broker.data.PersistentClientType;
import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.SubscriberAnalysisResult;
import org.thingsboard.mqtt.broker.data.SubscriberGroup;
import org.thingsboard.mqtt.broker.service.DummyClientService;
import org.thingsboard.mqtt.broker.service.PersistedMqttClientService;
import org.thingsboard.mqtt.broker.service.PublishStats;
import org.thingsboard.mqtt.broker.service.PublisherService;
import org.thingsboard.mqtt.broker.service.SubscribeStats;
import org.thingsboard.mqtt.broker.service.SubscriberService;
import org.thingsboard.mqtt.broker.util.ValidationUtil;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class MqttPerformanceTest {
    public static final long TEST_RUN_ID = new Random().nextLong();

    private final DummyClientService dummyClientService;
    private final SubscriberService subscriberService;
    private final PublisherService publisherService;
    private final PersistedMqttClientService persistedMqttClientService;
    private final TestRunConfiguration testRunConfiguration;

    @PostConstruct
    public void init() throws Exception {
        ValidationUtil.validateSubscriberGroups(testRunConfiguration.getSubscribersConfig());
        ValidationUtil.validatePublisherGroups(testRunConfiguration.getPublishersConfig());
        log.info("Start performance test.");

        printTestRunConfiguration();


        persistedMqttClientService.clearPersistedSessions();
        persistedMqttClientService.removeApplicationClients();
        Thread.sleep(2000);
        persistedMqttClientService.initApplicationClients();

        subscriberService.connectSubscribers();

        SubscribeStats subscribeStats = subscriberService.subscribe();
        DescriptiveStatistics generalLatencyStats = subscribeStats.getLatencyStats();

        publisherService.connectPublishers();

        dummyClientService.connectDummyClients();

        PublishStats publishStats = publisherService.startPublishing();


        Thread.sleep(TimeUnit.SECONDS.toMillis(testRunConfiguration.getSecondsToRun() + testRunConfiguration.getAdditionalSecondsToWait()));

        subscriberService.disconnectSubscribers();
        publisherService.disconnectPublishers();
        dummyClientService.disconnectDummyClients();

        // wait for all MQTT clients to close
        Thread.sleep(1000);

        persistedMqttClientService.clearPersistedSessions();

        SubscriberAnalysisResult analysisResult = subscriberService.analyzeReceivedMessages();
        DescriptiveStatistics acknowledgedStats = publishStats.getPublishAcknowledgedStats();
        DescriptiveStatistics sentStats = publishStats.getPublishSentLatencyStats();

        log.info("Latency stats: avg - {}, median - {}, max - {}, min - {}, 95th - {}, lost messages - {}, duplicated messages - {}, total received messages - {}, " +
                        "old received messages - {}, publish sent messages - {}, publish sent latency median - {}, publish sent latency max - {}, " +
                        "publish acknowledged messages - {}, publish acknowledged latency median - {}, publish acknowledged latency max - {}.",
                generalLatencyStats.getSum() / generalLatencyStats.getN(),
                generalLatencyStats.getMean(), generalLatencyStats.getMax(),
                generalLatencyStats.getMin(), generalLatencyStats.getPercentile(95),
                analysisResult.getLostMessages(), analysisResult.getDuplicatedMessages(),
                generalLatencyStats.getN(), subscribeStats.getOldMessagesCount().get(),
                sentStats.getN(), sentStats.getMean(), sentStats.getMax(),
                acknowledgedStats.getN(), acknowledgedStats.getMean(), acknowledgedStats.getMax()
                );

        publisherService.printDebugPublishersStats();
        subscriberService.printDebugSubscribersStats();

        // wait for all MQTT clients to close
        Thread.sleep(1000);
    }

    private void printTestRunConfiguration() {
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
        log.info("Test run info: test run ID - {}, publishers - {}, non-persistent subscribers - {}, regular persistent subscribers - {}, " +
                        "'APPLICATION' persistent subscribers - {}, dummy client connections - {}, " +
                        "publisher QoS - {}, subscriber QoS - {}, max messages per second - {}, " +
                        "run time - {}s, total published messages - {}, expected total received messages - {}, " +
                        "payload size - {}, configuration name - {}",
                MqttPerformanceTest.TEST_RUN_ID, totalPublishers, nonPersistedSubscribers, persistedDevicesSubscribers,
                persistedApplicationsSubscribers, testRunConfiguration.getNumberOfDummyClients(),
                testRunConfiguration.getPublisherQoS(), testRunConfiguration.getSubscriberQoS(), testRunConfiguration.getMaxMessagesPerPublisherPerSecond(),
                testRunConfiguration.getSecondsToRun(), totalPublishedMessages, totalExpectedReceivedMessages,
                testRunConfiguration.getPayloadSize(), testRunConfiguration.getConfigurationName());
    }
}
