package org.thingsboard.mqtt.broker.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.data.NodeType;

@Component
public class NodeTypeConfig {
    @Getter
    @Value("${test-run.test-app-type}")
    private NodeType nodeType;
}
