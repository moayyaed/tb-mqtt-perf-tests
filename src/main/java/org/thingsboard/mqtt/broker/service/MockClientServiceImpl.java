package org.thingsboard.mqtt.broker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.MqttClient;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockClientServiceImpl implements MockClientService {
    private final ClientInitializer clientInitializer;

    private List<MqttClient> mockClients;

    @Override
    public void connectMockClients(int numberOfClients) {
        log.info("Start connecting mock clients.");
        this.mockClients = new ArrayList<>(numberOfClients);
        for (int i = 0; i < numberOfClients; i++) {
            MqttClient mockClient = clientInitializer.initClient("test_mock_client_" + i);
            mockClients.add(mockClient);
        }
        log.info("Finished connecting mock clients.");
    }

    @Override
    public void disconnectMockClients() {
        log.info("Disconnecting mock clients.");
        int clientIndex = 0;
        for (MqttClient mockClient : mockClients) {
            try {
                mockClient.disconnect();
            } catch (Exception e) {
                log.error("[{}] Failed to disconnect mock client", clientIndex);
            }
            clientIndex++;
        }
        mockClients = null;
    }

    @PreDestroy
    public void destroy() {
        if (mockClients != null) {
            disconnectMockClients();
        }
    }
}
