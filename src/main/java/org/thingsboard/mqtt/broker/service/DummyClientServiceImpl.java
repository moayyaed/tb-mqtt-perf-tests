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
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.client.mqtt.MqttClient;
import org.thingsboard.mqtt.broker.client.mqtt.MqttConnectResult;
import org.thingsboard.mqtt.broker.config.TestRunConfiguration;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class DummyClientServiceImpl implements DummyClientService {
    private final ClientInitializer clientInitializer;
    private final TestRunConfiguration testRunConfiguration;

    private Map<String, MqttClient> dummyClients;

    @Override
    public void connectDummyClients() throws InterruptedException {
        this.dummyClients = new ConcurrentHashMap<>();
        DescriptiveStatistics connectionStats = new DescriptiveStatistics();
        CountDownLatch countDownLatch = new CountDownLatch(testRunConfiguration.getNumberOfDummyClients());

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        long connectionStart = System.currentTimeMillis();
        for (int i = 0; i < testRunConfiguration.getNumberOfDummyClients(); i++) {
            String clientId = "test_dummy_client_" + i;
            MqttClient dummyClient = clientInitializer.createClient(clientId);
            Future<MqttConnectResult> connectResultFuture = clientInitializer.connectClient(dummyClient);
            connectResultFuture.addListener(future -> {
                if (!future.isSuccess()) {
                    log.warn("Failed to connect dummy client {}", clientId);
                    dummyClient.disconnect();
                } else {
                    dummyClients.put(clientId, dummyClient);
                    connectionStats.addValue(System.currentTimeMillis() - connectionStart);
                }
                countDownLatch.countDown();
            });
        }

        countDownLatch.await(30, TimeUnit.SECONDS);
        stopWatch.stop();

        log.info("Connecting {} dummy clients took {} ms, median connection time - {}, max connection time - {}, 95 percentile connection time - {}.",
                testRunConfiguration.getNumberOfDummyClients(), stopWatch.getTime(), connectionStats.getMean(),
                connectionStats.getMax(), connectionStats.getPercentile(95.0));
    }

    @Override
    public void disconnectDummyClients() {
        log.info("Disconnecting dummy clients.");
        int clientIndex = 0;
        for (MqttClient dummyClient : dummyClients.values()) {
            try {
                dummyClient.disconnect();
            } catch (Exception e) {
                log.error("[{}] Failed to disconnect dummy client", clientIndex);
            }
            clientIndex++;
        }
        dummyClients = null;
    }

    @PreDestroy
    public void destroy() {
        if (dummyClients != null) {
            disconnectDummyClients();
        }
    }
}
