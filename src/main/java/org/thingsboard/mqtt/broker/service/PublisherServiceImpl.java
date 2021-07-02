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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.client.mqtt.MqttClient;
import org.thingsboard.mqtt.broker.client.mqtt.MqttConnectResult;
import org.thingsboard.mqtt.broker.client.mqtt.PublishFutures;
import org.thingsboard.mqtt.broker.config.TestRunConfiguration;
import org.thingsboard.mqtt.broker.data.Message;
import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.PublisherInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class PublisherServiceImpl implements PublisherService {
    private final ObjectMapper mapper = new ObjectMapper();

    private final ClientInitializer clientInitializer;
    private final TestRunConfiguration testRunConfiguration;

    private final Map<String, PublisherInfo> publisherInfos = new ConcurrentHashMap<>();
    private final ScheduledExecutorService publishScheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void connectPublishers() {
        int totalPublishers = testRunConfiguration.getPublishersConfig().stream().mapToInt(PublisherGroup::getPublishers).sum();
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        CountDownLatch connectCDL = new CountDownLatch(totalPublishers);
        for (PublisherGroup publisherGroup : testRunConfiguration.getPublishersConfig()) {
            for (int i = 0; i < publisherGroup.getPublishers(); i++) {
                String clientId = publisherGroup.getClientId(i);
                String topic = publisherGroup.getTopicPrefix() + i;
                MqttClient pubClient = clientInitializer.createClient(clientId);
                Future<MqttConnectResult> connectResultFuture = clientInitializer.connectClient(pubClient);
                connectResultFuture.addListener(future -> {
                    if (!future.isSuccess()) {
                        log.warn("[{}] Failed to connect publisher", clientId);
                        pubClient.disconnect();
                    } else {
                        publisherInfos.put(clientId, new PublisherInfo(pubClient, clientId, topic,
                                publisherGroup.isDebugEnabled() ? new DescriptiveStatistics() : null));
                    }
                    connectCDL.countDown();
                });
            }
        }
        try {
            connectCDL.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Failed to wait for the publishers to connect.");
            throw new RuntimeException("Failed to wait for the publishers to connect");
        }
        stopWatch.stop();
        log.info("Connecting {} publishers took {} ms.", totalPublishers, stopWatch.getTime());
    }

    @Override
    public PublishStats startPublishing() {
        DescriptiveStatistics publishSentLatencyStats = new DescriptiveStatistics();
        DescriptiveStatistics publishAcknowledgedStats = new DescriptiveStatistics();
        AtomicInteger publishedMessagesPerPublisher = new AtomicInteger();
        int publishPeriodMs = 1000 / testRunConfiguration.getMaxMessagesPerPublisherPerSecond();
        AtomicLong lastPublishTickTime = new AtomicLong(System.currentTimeMillis());
        publishScheduler.scheduleAtFixedRate(() -> {
            if (publishedMessagesPerPublisher.getAndIncrement() >= testRunConfiguration.getTotalPublisherMessagesCount()) {
                return;
            }
            long now = System.currentTimeMillis();
            long actualPublishTickPause = now - lastPublishTickTime.getAndSet(now);
            if (actualPublishTickPause > publishPeriodMs * 1.5) {
                log.debug("Pause between ticks is bigger than expected, expected pause - {} ms, actual pause - {} ms", publishPeriodMs, actualPublishTickPause);
            }
            for (PublisherInfo publisherInfo : publisherInfos.values()) {
                try {
                    Message message = new Message(System.currentTimeMillis(), generatePayload(testRunConfiguration.getPayloadSize()));
                    byte[] messageBytes = mapper.writeValueAsBytes(message);
                    PublishFutures publishFutures = publisherInfo.getPublisher().publish(publisherInfo.getTopic(), toByteBuf(messageBytes), testRunConfiguration.getPublisherQoS());
                    publishFutures.getPublishSentFuture()
                            .addListener(future -> {
                                        if (!future.isSuccess()) {
                                            log.error("[{}] Error sending msg", publisherInfo.getClientId());
                                        } else {
                                            publishSentLatencyStats.addValue(System.currentTimeMillis() - message.getCreateTime());
                                        }
                                    }
                            );
                    publishFutures.getPublishFinishedFuture()
                            .addListener(future -> {
                                        if (!future.isSuccess()) {
                                            log.error("[{}] Error acknowledging msg", publisherInfo.getClientId());
                                        } else {
                                            long ackLatency = System.currentTimeMillis() - message.getCreateTime();
                                            publishAcknowledgedStats.addValue(ackLatency);
                                            if (publisherInfo.getAcknowledgeLatencyStats() != null) {
                                                publisherInfo.getAcknowledgeLatencyStats().addValue(ackLatency);
                                            }
                                        }
                                    }
                            );
                } catch (Exception e) {
                    log.error("[{}] Failed to publish", publisherInfo.getClientId(), e);
                }
            }
        }, 0, publishPeriodMs, TimeUnit.MILLISECONDS);
        return new PublishStats(publishSentLatencyStats, publishAcknowledgedStats);
    }

    @Override
    public void disconnectPublishers() {
        log.info("Disconnecting publishers.");
        publishScheduler.shutdownNow();
        for (PublisherInfo publisherInfo : publisherInfos.values()) {
            try {
                publisherInfo.getPublisher().disconnect();
            } catch (Exception e) {
                log.error("[{}] Failed to disconnect publisher", publisherInfo.getClientId());
            }
        }
    }

    @Override
    public void printDebugPublishersStats() {
        for (PublisherInfo publisherInfo : publisherInfos.values()) {
            DescriptiveStatistics stats = publisherInfo.getAcknowledgeLatencyStats();
            if (stats != null) {
                log.info("[{}] Publish acknowledge latency: messages - {}, median - {}, 95 percentile - {}, max - {}.",
                        publisherInfo.getClientId(), stats.getN(), stats.getMean(), stats.getPercentile(95), stats.getMax());
            }
        }
    }

    private static byte[] generatePayload(int size) {
        byte[] payload = new byte[size];
        ThreadLocalRandom.current().nextBytes(payload);
        return payload;
    }

    private static final ByteBufAllocator ALLOCATOR = new UnpooledByteBufAllocator(false);

    private static ByteBuf toByteBuf(byte[] bytes) {
        ByteBuf payload = ALLOCATOR.buffer();
        payload.writeBytes(bytes);
        return payload;
    }
}
