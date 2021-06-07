package org.thingsboard.mqtt.broker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.SubscriberAnalysisResult;
import org.thingsboard.mqtt.broker.data.SubscriberGroup;
import org.thingsboard.mqtt.broker.data.SubscriberInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final List<SubscriberInfo> subscriberInfos = new ArrayList<>();

    @Override
    public void startSubscribers(Collection<SubscriberGroup> subscriberGroups, MqttMsgProcessor msgProcessor) {
        validateSubscriberGroups(subscriberGroups);
        int totalSubscribers = subscriberGroups.stream().mapToInt(SubscriberGroup::getSubscribers).sum();
        CountDownLatch subscribeCDL = new CountDownLatch(totalSubscribers);
        log.info("Start connecting {} subscribers.", totalSubscribers);
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        for (SubscriberGroup subscriberGroup : subscriberGroups) {
            for (int i = 0; i < subscriberGroup.getSubscribers(); i++) {
                int subscriberId = i;
                String clientId = subscriberGroup.getClientId(subscriberId);
                boolean cleanSession = subscriberGroup.getPersistentSessionInfo() == null;
                MqttClient subClient = clientInitializer.initClient(clientId, cleanSession);
                AtomicInteger receivedMsgs = new AtomicInteger(0);
                subscriberInfos.add(new SubscriberInfo(subClient, subscriberId, receivedMsgs, subscriberGroup));

                AtomicBoolean successfullySubscribed = new AtomicBoolean(false);
                subClient.on(subscriberGroup.getTopicFilter(), (topic, mqttMessageByteBuf) -> {
                    try {
                        msgProcessor.process(mqttMessageByteBuf);
                    } catch (Exception e) {
                        log.error("[{}] Failed to process msg", subscriberId);
                    }
                    receivedMsgs.incrementAndGet();
                }).addListener(future -> {
                    if (successfullySubscribed.getAndSet(true)) {
                        log.warn("[{}] Subscribed to topic more than one time!", subscriberId);
                    } else {
                        subscribeCDL.countDown();
                    }
                });
            }
        }
        try {
            subscribeCDL.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Failed to wait for the subscribers to initialize.");
            throw new RuntimeException("Failed to wait for the subscribers to initialize");
        }
        stopWatch.stop();
        log.info("Connecting subscribers took {} ms.", stopWatch.getTime());
    }

    @Override
    public void disconnectSubscribers() {
        log.info("Disconnecting subscribers.");
        for (SubscriberInfo subscriberInfo : subscriberInfos) {
            try {
                subscriberInfo.getSubscriber().disconnect();
            } catch (Exception e) {
                log.error("[{}] Failed to disconnect subscriber", subscriberInfo.getId());
            }
        }
    }

    @Override
    public SubscriberAnalysisResult analyzeReceivedMessages(Collection<PublisherGroup> publisherGroups, int totalProducerMessagesCount) {
        Map<Integer, PublisherGroup> publisherGroupsById = publisherGroups.stream()
                .collect(Collectors.toMap(PublisherGroup::getId, Function.identity()));
        int lostMessages = 0;
        int duplicatedMessages = 0;
        for (SubscriberInfo subscriberInfo : subscriberInfos) {
            int expectedReceivedMsgs = getSubscriberExpectedReceivedMsgs(totalProducerMessagesCount, publisherGroupsById, subscriberInfo.getSubscriberGroup());
            int actualReceivedMsgs = subscriberInfo.getReceivedMsgs().get();
            if (actualReceivedMsgs != expectedReceivedMsgs) {
                log.error("[{}] Expected messages count - {}, actual messages count - {}",
                        subscriberInfo.getId(), expectedReceivedMsgs, actualReceivedMsgs);
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
    public int calculateTotalExpectedReceivedMessages(Collection<SubscriberGroup> subscriberGroups, Collection<PublisherGroup> publisherGroups, int totalProducerMessagesCount) {
        Map<Integer, PublisherGroup> publisherGroupsById = publisherGroups.stream()
                .collect(Collectors.toMap(PublisherGroup::getId, Function.identity()));
        return subscriberGroups.stream()
                .mapToInt(subscriberGroup -> subscriberGroup.getSubscribers() * getSubscriberExpectedReceivedMsgs(totalProducerMessagesCount, publisherGroupsById, subscriberGroup))
                .sum();
    }

    private int getSubscriberExpectedReceivedMsgs(int totalProducerMessagesCount, Map<Integer, PublisherGroup> publisherGroupsById, SubscriberGroup subscriberGroup) {
        return subscriberGroup.getExpectedPublisherGroups().stream()
                .map(publisherGroupsById::get)
                .map(PublisherGroup::getPublishers)
                .mapToInt(Integer::intValue)
                .map(publishersInGroup -> publishersInGroup * totalProducerMessagesCount)
                .sum();
    }

    private void validateSubscriberGroups(Collection<SubscriberGroup> subscriberGroups) {
        Set<Integer> distinctIds = subscriberGroups.stream().map(SubscriberGroup::getId).collect(Collectors.toSet());
        if (distinctIds.size() != subscriberGroups.size()) {
            log.error("Some subscriber IDs are equal.");
            throw new RuntimeException("Some subscriber IDs are equal");
        }
    }
}
