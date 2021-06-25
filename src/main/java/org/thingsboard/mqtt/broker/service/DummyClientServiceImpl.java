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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.client.mqtt.MqttClient;
import org.thingsboard.mqtt.broker.config.TestRunConfiguration;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class DummyClientServiceImpl implements DummyClientService {
    private final ClientInitializer clientInitializer;
    private final TestRunConfiguration testRunConfiguration;

    private List<MqttClient> dummyClients;

    @Override
    public void connectDummyClients() {
        log.info("Start connecting dummy clients.");
        this.dummyClients = new ArrayList<>(testRunConfiguration.getNumberOfDummyClients());
        for (int i = 0; i < testRunConfiguration.getNumberOfDummyClients(); i++) {
            MqttClient dummyClient = clientInitializer.initClient("test_dummy_client_" + i);
            dummyClients.add(dummyClient);
        }
        log.info("Finished connecting dummy clients.");
    }

    @Override
    public void disconnectDummyClients() {
        log.info("Disconnecting dummy clients.");
        int clientIndex = 0;
        for (MqttClient dummyClient : dummyClients) {
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
