/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.data;

import lombok.Getter;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.thingsboard.mqtt.broker.client.mqtt.MqttClient;

@Getter
public class PublisherInfo {
    private final MqttClient publisher;
    private final String clientId;
    private final String topic;
    private final DescriptiveStatistics acknowledgeLatencyStats;
    private final boolean debug;

    public PublisherInfo(MqttClient publisher, String clientId, String topic, DescriptiveStatistics acknowledgeLatencyStats) {
        this.publisher = publisher;
        this.clientId = clientId;
        this.topic = topic;
        this.acknowledgeLatencyStats = acknowledgeLatencyStats;
        this.debug = acknowledgeLatencyStats != null;
    }

    public PublisherInfo(MqttClient publisher, String clientId, String topic) {
        this.publisher = publisher;
        this.clientId = clientId;
        this.topic = topic;
        this.acknowledgeLatencyStats = null;
        this.debug = false;
    }
}