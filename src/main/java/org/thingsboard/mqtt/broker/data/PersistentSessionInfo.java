package org.thingsboard.mqtt.broker.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PersistentSessionInfo {
    private final PersistentClientType clientType;
}
