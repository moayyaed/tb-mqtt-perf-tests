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
package org.thingsboard.mqtt.broker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.broker.config.TestRunConfiguration;
import org.thingsboard.mqtt.broker.data.Message;
import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.PublisherInfo;

import java.util.ArrayList;
import java.util.List;
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

    private final List<PublisherInfo> publisherInfos = new ArrayList<>();
    private final ScheduledExecutorService publishScheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void connectPublishers() {
        log.info("Start connecting publishers.");
        for (PublisherGroup publisherGroup : testRunConfiguration.getPublishersConfig()) {
            for (int i = 0; i < publisherGroup.getPublishers(); i++) {
                String clientId = publisherGroup.getClientId(i);
                MqttClient pubClient = clientInitializer.initClient(clientId);
                String topic = publisherGroup.getTopicPrefix() + i;
                publisherInfos.add(new PublisherInfo(pubClient, i, topic));
            }
        }
        log.info("Finished connecting publishers.");
    }

    @Override
    public DescriptiveStatistics startPublishing() {
        DescriptiveStatistics publisherLatencyStats = new DescriptiveStatistics();
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
            for (PublisherInfo publisherInfo : publisherInfos) {
                try {
                    Message message = new Message(System.currentTimeMillis(), generatePayload(testRunConfiguration.getPayloadSize()));
                    byte[] messageBytes = mapper.writeValueAsBytes(message);
                    publisherInfo.getPublisher().publish(publisherInfo.getTopic(), toByteBuf(messageBytes), testRunConfiguration.getPublisherQoS())
                            .addListener(future -> {
                                        if (!future.isSuccess()) {
                                            log.error("[{}] Error publishing msg", publisherInfo.getId());
                                        } else {
                                            publisherLatencyStats.addValue(System.currentTimeMillis() - message.getCreateTime());
                                        }
                                    }
                            );
                } catch (Exception e) {
                    log.error("[{}] Failed to publish", publisherInfo.getId(), e);
                }
            }
        }, 0, publishPeriodMs, TimeUnit.MILLISECONDS);
        return publisherLatencyStats;
    }

    @Override
    public void disconnectPublishers() {
        log.info("Disconnecting publishers.");
        publishScheduler.shutdownNow();
        for (PublisherInfo publisherInfo : publisherInfos) {
            try {
                publisherInfo.getPublisher().disconnect();
            } catch (Exception e) {
                log.error("[{}] Failed to disconnect publisher", publisherInfo.getId());
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
