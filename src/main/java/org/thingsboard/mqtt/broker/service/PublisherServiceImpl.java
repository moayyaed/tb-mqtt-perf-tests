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
package org.thingsboard.mqtt.broker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.client.mqtt.MqttClient;
import org.thingsboard.mqtt.broker.config.TestRunClusterConfig;
import org.thingsboard.mqtt.broker.config.TestRunConfiguration;
import org.thingsboard.mqtt.broker.data.Message;
import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.PublisherInfo;
import org.thingsboard.mqtt.broker.tests.MqttPerformanceTest;
import org.thingsboard.mqtt.broker.util.CallbackUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class PublisherServiceImpl implements PublisherService {
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${test-run.publisher-warmup-wait-time}")
    private int warmupWaitTime;
    @Value("${test-run.publisher_clients_persistent:false}")
    private boolean publisherClientsPersistent;
    @Value("${test-run.clear-persisted-sessions-wait-time}")
    private int waitTime;

    private final ClientInitializer clientInitializer;
    private final TestRunConfiguration testRunConfiguration;
    private final ClientIdService clientIdService;
    private final TestRunClusterConfig testRunClusterConfig;
    private final PayloadGenerator payloadGenerator;
    private final ClusterProcessService clusterProcessService;

    private final Map<String, PublisherInfo> publisherInfos = new ConcurrentHashMap<>();
    private final ScheduledExecutorService publishScheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void connectPublishers() {
        List<PreConnectedPublisherInfo> preConnectedPublisherInfos = new ArrayList<>();
        int currentPublisherId = 0;
        for (PublisherGroup publisherGroup : testRunConfiguration.getPublishersConfig()) {
            for (int i = 0; i < publisherGroup.getPublishers(); i++) {
                if (currentPublisherId++ % testRunClusterConfig.getParallelTestsCount() == testRunClusterConfig.getSequentialNumber()) {
                    preConnectedPublisherInfos.add(new PreConnectedPublisherInfo(publisherGroup, i));
                }
            }
        }
        clusterProcessService.process("PUBLISHERS_CONNECT", preConnectedPublisherInfos, (latch, preConnectedPublisherInfo) -> {
            PublisherGroup publisherGroup = preConnectedPublisherInfo.getPublisherGroup();
            int publisherIndex = preConnectedPublisherInfo.getPublisherIndex();
            String clientId = clientIdService.createPublisherClientId(publisherGroup, publisherIndex);
            String topic = publisherGroup.getTopicPrefix() + publisherIndex;
            MqttClient pubClient = clientInitializer.createClient(clientId, MqttPerformanceTest.DEFAULT_USER_NAME, !publisherClientsPersistent);
            clientInitializer.connectClient(CallbackUtil.createConnectCallback(
                            connectResult -> {
                                publisherInfos.put(clientId, new PublisherInfo(pubClient, clientId, topic,
                                        publisherGroup.isDebugEnabled() ? new DescriptiveStatistics() : null));
                                latch.countDown();
                            }, t -> {
                                log.warn("[{}] Failed to connect publisher", clientId);
                                pubClient.disconnect();
                                latch.countDown();
                            }
                    ),
                    pubClient);
        });
    }

    @Override
    public void warmUpPublishers() throws Exception {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        CountDownLatch warmupCDL = new CountDownLatch(publisherInfos.size());
        AtomicBoolean successfulWarmUp = new AtomicBoolean(true);
        for (PublisherInfo publisherInfo : publisherInfos.values()) {
            try {
                Message message = new Message(System.currentTimeMillis(), true, payloadGenerator.generatePayload());
                publisherInfo.getPublisher().publish(publisherInfo.getTopic(), toByteBuf(mapper.writeValueAsBytes(message)),
                        CallbackUtil.createCallback(
                                warmupCDL::countDown,
                                t -> {
                                    successfulWarmUp.getAndSet(false);
                                    log.error("[{}] Error acknowledging warmup msg", publisherInfo.getClientId(), t);
                                    warmupCDL.countDown();
                                }),
                        MqttQoS.AT_MOST_ONCE);
            } catch (Exception e) {
                log.error("[{}] Failed to publish", publisherInfo.getClientId(), e);
                throw e;
            }
        }

        boolean successfulWait = warmupCDL.await(warmupWaitTime, TimeUnit.SECONDS);
        if (!successfulWait || !successfulWarmUp.get()) {
            throw new RuntimeException("Failed to warm up publishers. " + warmupCDL.getCount() + " publishers couldn't acknowledge a message");
        }

        stopWatch.stop();
        log.info("Warming up {} publishers took {} ms.", publisherInfos.size(), stopWatch.getTime());
    }

    @Override
    public PublishStats startPublishing() {
        DescriptiveStatistics publishSentLatencyStats = new DescriptiveStatistics();
        DescriptiveStatistics publishAcknowledgedStats = new DescriptiveStatistics();
        AtomicInteger publishedMessagesPerPublisher = new AtomicInteger();
        int publishPeriodMs = 1000 / testRunConfiguration.getMaxMessagesPerPublisherPerMinute() * 60;
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
                    Message message = new Message(System.currentTimeMillis(), false, payloadGenerator.generatePayload());
                    byte[] messageBytes = mapper.writeValueAsBytes(message);
                    ChannelFuture publishSentFuture = publisherInfo.getPublisher().publish(publisherInfo.getTopic(), toByteBuf(messageBytes),
                            CallbackUtil.createCallback(
                                    () -> {
                                        long ackLatency = System.currentTimeMillis() - message.getCreateTime();
                                        publishAcknowledgedStats.addValue(ackLatency);
                                        if (publisherInfo.isDebug()) {
                                            publisherInfo.getAcknowledgeLatencyStats().addValue(ackLatency);
                                            log.debug("[{}] Acknowledged msg with time {}", publisherInfo.getClientId(), message.getCreateTime());
                                        }
                                    },
                                    t -> {
                                        log.debug("[{}] Failed to send msg. Exception - {}, message - {}", publisherInfo.getClientId(), t.getClass().getSimpleName(), t.getMessage());
                                    }
                            ),
                            testRunConfiguration.getPublisherQoS());
                    publishSentFuture
                            .addListener(future -> {
                                        if (!future.isSuccess()) {
                                            log.debug("[{}] Error sending msg, reason - {}", publisherInfo.getClientId(), future.cause().getMessage());
                                        } else {
                                            publishSentLatencyStats.addValue(System.currentTimeMillis() - message.getCreateTime());
                                            if (publisherInfo.isDebug()) {
                                                log.debug("[{}] Sent msg with time {}", publisherInfo.getClientId(), message.getCreateTime());
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
    public void clearPersistedSessions() throws InterruptedException {
        if (publisherClientsPersistent) {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            CountDownLatch countDownLatch = new CountDownLatch(publisherInfos.size());
            for (PublisherInfo publisherInfo : publisherInfos.values()) {
                publisherInfo.getPublisher().getClientConfig().setCleanSession(true);
                clientInitializer.connectClient(CallbackUtil.createConnectCallback(
                                connectResult -> {
                                    publisherInfo.getPublisher().disconnect();
                                    countDownLatch.countDown();
                                }, t -> {
                                    log.warn("[{}] Failed to clear publisher persisted session", publisherInfo.getPublisher().getClientConfig().getClientId());
                                    publisherInfo.getPublisher().disconnect();
                                    countDownLatch.countDown();
                                }
                        ),
                        publisherInfo.getPublisher());
            }

            var result = countDownLatch.await(waitTime, TimeUnit.SECONDS);
            log.info("The result of await processing for publisher clients clear persisted sessions is: {}", result);
            stopWatch.stop();
            log.info("Clearing {} publisher persisted sessions took {} ms", publisherInfos.size(), stopWatch.getTime());
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

    private static ByteBuf toByteBuf(byte[] bytes) {
        return Unpooled.wrappedBuffer(bytes);
    }

    @Getter
    @AllArgsConstructor
    private static class PreConnectedPublisherInfo {
        private final PublisherGroup publisherGroup;
        private final int publisherIndex;
    }
}
