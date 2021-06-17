/**
 * Copyright © 2016-2018 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.config;

import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.data.PersistentClientType;
import org.thingsboard.mqtt.broker.data.PersistentSessionInfo;
import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.SubscriberGroup;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${test-run-configuration-file:}'==''")
public class DefaultTestRunConfiguration implements TestRunConfiguration {
    private static final String CONFIGURATION_NAME = "Default Configuration";

    private static final List<PublisherGroup> publisherGroupsConfiguration = Arrays.asList(
            new PublisherGroup(1, 200, "europe/ua/kyiv/tb/"),
            new PublisherGroup(2, 50, "europe/ua/kyiv/"),
            new PublisherGroup(3, 150, "asia/")
//            new PublisherGroup(4, 1, "perf/test/topic/", "perf_test_publisher_")
    );

    private static final Set<Integer> ALL_PUBLISHER_IDS = publisherGroupsConfiguration.stream().map(PublisherGroup::getId).collect(Collectors.toSet());

    private static final List<SubscriberGroup> subscriberGroupsConfiguration = Arrays.asList(
            new SubscriberGroup(1, 150, "europe/ua/kyiv/tb/+", Set.of(1), null),
//            new SubscriberGroup(2, 50, "europe/ua/kyiv/#", Set.of(1, 2), null),
            new SubscriberGroup(3, 20, "#", ALL_PUBLISHER_IDS, new PersistentSessionInfo(PersistentClientType.APPLICATION)),
            new SubscriberGroup(4, 20, "europe/ua/kyiv/tb/#", Set.of(1), new PersistentSessionInfo(PersistentClientType.DEVICE)),
            new SubscriberGroup(5, 10, "europe/ua/kyiv/#", Set.of(1, 2), new PersistentSessionInfo(PersistentClientType.DEVICE))
//            new SubscriberGroup(6, 1, "europe/ua/kyiv/#", Set.of(1, 2), new PersistentSessionInfo(PersistentClientType.APPLICATION))
//            new SubscriberGroup(7, 1, "perf/test/topic/+", Set.of(4), null, "perf_test_basic_")
//            new SubscriberGroup(8, 1, "perf/test/topic/+", Set.of(4), new PersistentSessionInfo(PersistentClientType.DEVICE), "perf_test_device_")
//            new SubscriberGroup(9, 1, "perf/test/topic/+", Set.of(4), new PersistentSessionInfo(PersistentClientType.APPLICATION), "perf_test_application_")
    );

    private static final int SECONDS_TO_RUN = 30;
    private static final int ADDITIONAL_SECONDS_TO_WAIT = 30;

    private static final int DUMMY_CLIENTS = 1000;
    private static final int MAX_MSGS_PER_PUBLISHER_PER_SECOND = 1;

    private static final MqttQoS PUBLISHER_QOS = MqttQoS.AT_LEAST_ONCE;
    private static final MqttQoS SUBSCRIBER_QOS = MqttQoS.AT_LEAST_ONCE;

    private static final int TOTAL_PUBLISHER_MESSAGES = SECONDS_TO_RUN * MAX_MSGS_PER_PUBLISHER_PER_SECOND;

    @Override
    public String getConfigurationName() {
        return CONFIGURATION_NAME;
    }

    @Override
    public List<SubscriberGroup> getSubscribersConfig() {
        return subscriberGroupsConfiguration;
    }

    @Override
    public List<PublisherGroup> getPublishersConfig() {
        return publisherGroupsConfiguration;
    }

    @Override
    public int getMaxMessagesPerPublisherPerSecond() {
        return MAX_MSGS_PER_PUBLISHER_PER_SECOND;
    }

    @Override
    public int getSecondsToRun() {
        return SECONDS_TO_RUN;
    }

    @Override
    public int getAdditionalSecondsToWait() {
        return ADDITIONAL_SECONDS_TO_WAIT;
    }

    @Override
    public int getTotalPublisherMessagesCount() {
        return TOTAL_PUBLISHER_MESSAGES;
    }

    @Override
    public int getNumberOfDummyClients() {
        return DUMMY_CLIENTS;
    }

    @Override
    public MqttQoS getPublisherQoS() {
        return PUBLISHER_QOS;
    }

    @Override
    public MqttQoS getSubscriberQoS() {
        return SUBSCRIBER_QOS;
    }
}
