package org.thingsboard.mqtt.broker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.mqtt.broker.data.dto.LoginDto;
import org.thingsboard.mqtt.broker.data.dto.LoginResponseDto;
import org.thingsboard.mqtt.broker.data.dto.MqttClientDto;

import javax.annotation.PostConstruct;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TbBrokerRestServiceImpl implements TbBrokerRestService {

    private final RestTemplateBuilder restTemplateBuilder;
    private RestTemplate restTemplate;

    @Value("${tb-broker.uri}")
    private String tbUri;
    @Value("${tb-broker.admin.username}")
    private String username;
    @Value("${tb-broker.admin.password}")
    private String password;

    @PostConstruct
    public void init() {
        this.restTemplate = restTemplateBuilder.build();

        LoginResponseDto loginResponse = restTemplate.postForEntity(tbUri + "/api/auth/login", new LoginDto(username, password), LoginResponseDto.class).getBody();
        if (loginResponse == null) {
           return;
        }

        log.info("Successfully logged into the ThingsBoard MQTT Broker.");
        this.restTemplate = restTemplateBuilder.defaultHeader("X-Authorization", "Bearer " + loginResponse.getToken()).build();
    }

    @Override
    public MqttClientDto getClient(String clientId) {
        return restTemplate.getForEntity(tbUri + "/api/mqtt/client/" + clientId, MqttClientDto.class).getBody();
    }



    @Override
    public void createClient(MqttClientDto clientDto) {
        MqttClientDto savedClientDto = restTemplate.postForEntity(tbUri + "/api/mqtt/client", clientDto, MqttClientDto.class).getBody();
        if (savedClientDto == null) {
            throw new RuntimeException("Failed to save MQTT client");
        }
    }

    @Override
    public void removeClient(String clientId) {
        restTemplate.delete(tbUri + "/api/mqtt/client/" + clientId);
    }
}
