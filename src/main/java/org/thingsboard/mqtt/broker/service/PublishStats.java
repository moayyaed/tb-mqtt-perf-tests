package org.thingsboard.mqtt.broker.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

@Getter
@AllArgsConstructor
public class PublishStats {
    private final DescriptiveStatistics publishSentLatencyStats;
    private final DescriptiveStatistics publishAcknowledgedStats;
}
