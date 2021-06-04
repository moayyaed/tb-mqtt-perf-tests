package org.thingsboard.mqtt.broker.service;

import org.thingsboard.mqtt.broker.data.dto.MqttClientDto;

import java.util.UUID;

public interface TbBrokerRestService {
    MqttClientDto getClient(String clientId);

    void createClient(MqttClientDto clientDto);

    void removeClient(String clientId);
}
