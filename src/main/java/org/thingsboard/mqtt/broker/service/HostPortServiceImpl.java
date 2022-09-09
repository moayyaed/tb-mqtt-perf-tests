/**
 * Copyright © 2016-2022 The Thingsboard Authors
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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.data.dto.HostPortDto;

import javax.annotation.PostConstruct;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class HostPortServiceImpl implements HostPortService {

    @Value("${mqtt.host}")
    private String mqttHost;
    @Value("${mqtt.ports}")
    private int[] mqttPorts;

    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    @PostConstruct
    public void init() {
        if (mqttPorts == null || mqttPorts.length == 0) {
            throw new RuntimeException("Not valid MQTT ports value");
        }
    }

    @Override
    public HostPortDto getHostPort() {
        return new HostPortDto(mqttHost, mqttPorts[ThreadLocalRandom.current().nextInt(mqttPorts.length)]);
    }
}
