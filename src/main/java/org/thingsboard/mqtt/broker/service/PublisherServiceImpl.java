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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    public void startPublishing() {
        AtomicInteger publishedMessagesPerPublisher = new AtomicInteger();
        publishScheduler.scheduleAtFixedRate(() -> {
            if (publishedMessagesPerPublisher.getAndIncrement() >= testRunConfiguration.getTotalPublisherMessagesCount()) {
                return;
            }
            for (PublisherInfo publisherInfo : publisherInfos) {
                try {
                    Message message = new Message(System.currentTimeMillis());
                    byte[] messageBytes = mapper.writeValueAsBytes(message);
                    publisherInfo.getPublisher().publish(publisherInfo.getTopic(), toByteBuf(messageBytes), testRunConfiguration.getPublisherQoS())
                            .addListener(future -> {
                                        if (!future.isSuccess()) {
                                            log.error("[{}] Error publishing msg", publisherInfo.getId());
                                        }
                                    }
                            );
                } catch (Exception e) {
                    log.error("[{}] Failed to publish", publisherInfo.getId(), e);
                }
            }
        }, 0, 1000 / testRunConfiguration.getMaxMessagesPerPublisherPerSecond(), TimeUnit.MILLISECONDS);
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


    private static final ByteBufAllocator ALLOCATOR = new UnpooledByteBufAllocator(false);

    private static ByteBuf toByteBuf(byte[] bytes) {
        ByteBuf payload = ALLOCATOR.buffer();
        payload.writeBytes(bytes);
        return payload;
    }
}
