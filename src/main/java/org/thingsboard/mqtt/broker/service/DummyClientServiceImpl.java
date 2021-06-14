package org.thingsboard.mqtt.broker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.MqttClient;
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
