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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.mqtt.broker.data.PageData;
import org.thingsboard.mqtt.broker.data.dto.LoginDto;
import org.thingsboard.mqtt.broker.data.dto.LoginResponseDto;
import org.thingsboard.mqtt.broker.data.dto.MqttClientDto;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${broker.type:}'=='THINGSBOARD'")
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
    public List<MqttClientDto> getAllClients() {
        List<MqttClientDto> clients = new ArrayList<>();
        boolean hasNext;
        int currentPage = 0;
        int pageSize = 10;
        do {
            PageData<MqttClientDto> pageData = restTemplate.exchange(tbUri + "/api/mqtt/client?pageSize=" + pageSize + "&page=" + currentPage,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<PageData<MqttClientDto>>() {
                    },
                    Map.of()
            ).getBody();
            hasNext = pageData.hasNext();
            clients.addAll(pageData.getData());
            currentPage++;
        } while (hasNext);
        return clients;
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
