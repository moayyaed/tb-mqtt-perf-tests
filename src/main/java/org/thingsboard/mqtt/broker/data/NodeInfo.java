package org.thingsboard.mqtt.broker.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeInfo {
    private String nodeUrl;
    private int nodesInCluster;
}
