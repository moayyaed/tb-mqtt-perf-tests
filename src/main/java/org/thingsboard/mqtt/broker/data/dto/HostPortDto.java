package org.thingsboard.mqtt.broker.data.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HostPortDto {
    private final String host;
    private final int port;
}
