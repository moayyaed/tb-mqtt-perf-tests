package org.thingsboard.mqtt.broker.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SubscriberAnalysisResult {
    private final int lostMessages;
    private final int duplicatedMessages;
    private final int expectedTotalReceivedMessages;
}
