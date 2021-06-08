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
