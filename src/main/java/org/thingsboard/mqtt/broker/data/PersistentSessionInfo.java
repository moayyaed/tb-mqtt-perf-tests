package org.thingsboard.mqtt.broker.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersistentSessionInfo {
    private PersistentClientType clientType;
}
