/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.tests.MqttPerformanceTest;
import org.thingsboard.mqtt.broker.util.CallbackUtil;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
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
    private final ClientIdService clientIdService;
    private final TestRunClusterConfig testRunClusterConfig;
    private final ClusterProcessService clusterProcessService;

    @Value("${test-run.dummy_clients_persistent:false}")
    private boolean dummyClientsPersistent;
    @Value("${test-run.clear-persisted-sessions-wait-time}")
    private int waitTime;

    private Map<String, MqttClient> dummyClients;

    @Override
    public void connectDummyClients() {
        this.dummyClients = new ConcurrentHashMap<>();
        DescriptiveStatistics connectionStats = new DescriptiveStatistics();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        List<Integer> preConnectedDummyIndexes = new ArrayList<>();
        int currentDummyId = 0;
        for (int i = 0; i < testRunConfiguration.getNumberOfDummyClients(); i++) {
            if (currentDummyId++ % testRunClusterConfig.getParallelTestsCount() == testRunClusterConfig.getSequentialNumber()) {
                preConnectedDummyIndexes.add(i);
            }
        }

        clusterProcessService.process("DUMMIES_CONNECT", preConnectedDummyIndexes, (latch, dummyId) -> {
            String clientId = clientIdService.createDummyClientId(dummyId);
            MqttClient dummyClient = clientInitializer.createClient(clientId, MqttPerformanceTest.DEFAULT_USER_NAME, !dummyClientsPersistent);
            long connectionStart = System.currentTimeMillis();
            clientInitializer.connectClient(CallbackUtil.createConnectCallback(connectResult -> {
                        dummyClients.put(clientId, dummyClient);
                        connectionStats.addValue(System.currentTimeMillis() - connectionStart);
                        latch.countDown();
                    }, t -> {
                        log.warn("Failed to connect dummy client {}", clientId);
                        dummyClient.disconnect();
                        latch.countDown();
                    }),
                    dummyClient);
        });

        stopWatch.stop();
        int totalNodeDummies = testRunConfiguration.getNumberOfDummyClients() / testRunClusterConfig.getParallelTestsCount()
                + (testRunConfiguration.getNumberOfDummyClients() % testRunClusterConfig.getParallelTestsCount() > testRunClusterConfig.getSequentialNumber() ? 1 : 0);
        log.info("Connecting {} dummy clients took {} ms, avg connection time - {}, max connection time - {}, 95 percentile connection time - {}.",
                totalNodeDummies, stopWatch.getTime(), connectionStats.getMean(),
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
        if (!dummyClientsPersistent) {
            dummyClients = null;
        }
    }

    @Override
    public void clearPersistedSessions() throws InterruptedException {
        if (dummyClientsPersistent) {
            log.info("Start clear dummy persisted Sessions.");
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            CountDownLatch countDownLatch = new CountDownLatch(dummyClients.size());
            for (MqttClient mqttClient : dummyClients.values()) {
                mqttClient.getClientConfig().setCleanSession(true);
                clientInitializer.connectClient(CallbackUtil.createConnectCallback(
                                connectResult -> {
                                    mqttClient.disconnect();
                                    countDownLatch.countDown();
                                }, t -> {
                                    log.warn("[{}] Failed to clear dummy persisted session", mqttClient.getClientConfig().getClientId());
                                    mqttClient.disconnect();
                                    countDownLatch.countDown();
                                }
                        ),
                        mqttClient);
            }

            var result = countDownLatch.await(waitTime, TimeUnit.SECONDS);
            log.info("The result of await processing for dummy clients clear persisted sessions is: {}", result);
            stopWatch.stop();
            log.info("Clearing {} dummy persisted sessions took {} ms", dummyClients.size(), stopWatch.getTime());
            dummyClients = null;
        }
    }

    @PreDestroy
    public void destroy() {
        if (dummyClients != null) {
            disconnectDummyClients();
        }
    }
}
