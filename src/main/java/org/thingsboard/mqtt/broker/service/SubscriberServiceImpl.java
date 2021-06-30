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

import io.netty.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.client.mqtt.MqttClient;
import org.thingsboard.mqtt.broker.config.TestRunConfiguration;
import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.SubscriberAnalysisResult;
import org.thingsboard.mqtt.broker.data.SubscriberGroup;
import org.thingsboard.mqtt.broker.data.SubscriberInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class SubscriberServiceImpl implements SubscriberService {
    private final ClientInitializer clientInitializer;
    private final TestRunConfiguration testRunConfiguration;

    private final Map<String, SubscriberInfo> subscriberInfos = new ConcurrentHashMap<>();

    @Override
    public void connectSubscribers(MqttMsgProcessor defaultMsgProcessor) {
        int totalSubscribers = testRunConfiguration.getSubscribersConfig().stream().mapToInt(SubscriberGroup::getSubscribers).sum();
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        CountDownLatch connectCDL = new CountDownLatch(totalSubscribers);
        for (SubscriberGroup subscriberGroup : testRunConfiguration.getSubscribersConfig()) {
            for (int i = 0; i < subscriberGroup.getSubscribers(); i++) {
                int subscriberId = i;
                String clientId = subscriberGroup.getClientId(subscriberId);
                boolean cleanSession = subscriberGroup.getPersistentSessionInfo() == null;
                MqttClient subClient = clientInitializer.createClient(clientId, cleanSession, (s, mqttMessageByteBuf) -> {
                    try {
                        defaultMsgProcessor.process(mqttMessageByteBuf);
                    } catch (Exception e) {
                        log.error("[{}] Failed to process msg", clientId);
                    }
                });
                clientInitializer.connectClient(subClient).addListener(future -> {
                    if (!future.isSuccess()) {
                        log.warn("[{}] Failed to connect subscriber", clientId);
                        subClient.disconnect();
                    } else {
                        subscriberInfos.put(clientId, new SubscriberInfo(subClient, subscriberId, clientId, new AtomicInteger(0), subscriberGroup));
                    }
                    connectCDL.countDown();
                });
            }
        }
        try {
            connectCDL.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Failed to wait for the subscribers to connect.");
            throw new RuntimeException("Failed to wait for the subscribers to connect");
        }
        stopWatch.stop();
        log.info("Connecting {} subscribers took {} ms.", totalSubscribers, stopWatch.getTime());
    }

    @Override
    public void subscribe(MqttMsgProcessor msgProcessor) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        CountDownLatch subscribeCDL = new CountDownLatch(subscriberInfos.size());
        for (SubscriberInfo subscriberInfo : subscriberInfos.values()) {
            Future<Void> subscribeFuture = subscriberInfo.getSubscriber().on(subscriberInfo.getSubscriberGroup().getTopicFilter(), (topic, mqttMessageByteBuf) -> {
                try {
                    msgProcessor.process(mqttMessageByteBuf);
                } catch (Exception e) {
                    log.error("[{}] Failed to process msg", subscriberInfo.getId());
                }
                subscriberInfo.getReceivedMsgs().incrementAndGet();
            }, testRunConfiguration.getSubscriberQoS());

            subscribeFuture.addListener(future -> {
                subscribeCDL.countDown();
            });
        }
        try {
            subscribeCDL.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Failed to wait for the subscribers to subscribe.");
            throw new RuntimeException("Failed to wait for the subscribers to subscribe");
        }
        stopWatch.stop();
        log.info("Subscribing {} subscribers took {} ms.", subscriberInfos.size(), stopWatch.getTime());
    }

    @Override
    public void disconnectSubscribers() {
        log.info("Disconnecting subscribers.");
        for (SubscriberInfo subscriberInfo : subscriberInfos.values()) {
            try {
                subscriberInfo.getSubscriber().disconnect();
            } catch (Exception e) {
                log.error("[{}] Failed to disconnect subscriber", subscriberInfo.getClientId());
            }
        }
    }

    @Override
    public SubscriberAnalysisResult analyzeReceivedMessages() {
        Map<Integer, PublisherGroup> publisherGroupsById = testRunConfiguration.getPublishersConfig().stream()
                .collect(Collectors.toMap(PublisherGroup::getId, Function.identity()));
        int lostMessages = 0;
        int duplicatedMessages = 0;
        for (SubscriberInfo subscriberInfo : subscriberInfos.values()) {
            int expectedReceivedMsgs = getSubscriberExpectedReceivedMsgs(testRunConfiguration.getTotalPublisherMessagesCount(), publisherGroupsById, subscriberInfo.getSubscriberGroup());
            int actualReceivedMsgs = subscriberInfo.getReceivedMsgs().get();
            if (actualReceivedMsgs != expectedReceivedMsgs) {
                log.error("[{}] Expected messages count - {}, actual messages count - {}",
                        subscriberInfo.getClientId(), expectedReceivedMsgs, actualReceivedMsgs);
                if (expectedReceivedMsgs > actualReceivedMsgs) {
                    lostMessages += expectedReceivedMsgs - actualReceivedMsgs;
                } else {
                    duplicatedMessages += actualReceivedMsgs - expectedReceivedMsgs;
                }
            }
        }
        return SubscriberAnalysisResult.builder()
                .lostMessages(lostMessages)
                .duplicatedMessages(duplicatedMessages)
                .build();
    }

    @Override
    public int calculateTotalExpectedReceivedMessages() {
        Map<Integer, PublisherGroup> publisherGroupsById = testRunConfiguration.getPublishersConfig().stream()
                .collect(Collectors.toMap(PublisherGroup::getId, Function.identity()));
        return testRunConfiguration.getSubscribersConfig().stream()
                .mapToInt(subscriberGroup -> subscriberGroup.getSubscribers() * getSubscriberExpectedReceivedMsgs(testRunConfiguration.getTotalPublisherMessagesCount(), publisherGroupsById, subscriberGroup))
                .sum();
    }

    private int getSubscriberExpectedReceivedMsgs(int totalProducerMessagesCount, Map<Integer, PublisherGroup> publisherGroupsById, SubscriberGroup subscriberGroup) {
        return subscriberGroup.getExpectedPublisherGroups().stream()
                .map(publisherGroupsById::get)
                .filter(Objects::nonNull)
                .map(PublisherGroup::getPublishers)
                .mapToInt(Integer::intValue)
                .map(publishersInGroup -> publishersInGroup * totalProducerMessagesCount)
                .sum();
    }
}
