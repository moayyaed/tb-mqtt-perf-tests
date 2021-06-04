package org.thingsboard.mqtt.broker.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.data.Message;
import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.SubscriberAnalysisResult;
import org.thingsboard.mqtt.broker.data.SubscriberGroup;
import org.thingsboard.mqtt.broker.service.MockClientService;
import org.thingsboard.mqtt.broker.service.PublisherService;
import org.thingsboard.mqtt.broker.service.SubscriberService;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttPerformanceTestService {
    private final ObjectMapper mapper = new ObjectMapper();

    private static final List<PublisherGroup> publisherGroupsConfiguration = Arrays.asList(
            new PublisherGroup(1, 150, "europe/ua/kyiv/tb/"),
            new PublisherGroup(2, 50, "europe/ua/kyiv/")
    );

    private static final List<SubscriberGroup> subscriberGroupsConfiguration = Arrays.asList(
            new SubscriberGroup(1, 5, "europe/ua/kyiv/tb/+", Set.of(1)),
            new SubscriberGroup(2, 5, "europe/ua/kyiv/#", Set.of(1, 2))
    );

    private static final int SECONDS_TO_RUN = 30;
    private static final int ADDITIONAL_SECONDS_TO_WAIT = 10;

    private static final int MOCK_CLIENTS = 5;
    private static final int MAX_MSGS_PER_PUBLISHER_PER_SECOND = 5;

    private static final int TOTAL_PUBLISHER_MESSAGES = SECONDS_TO_RUN * MAX_MSGS_PER_PUBLISHER_PER_SECOND;

    private final MockClientService mockClientService;
    private final SubscriberService subscriberService;
    private final PublisherService publisherService;

    @PostConstruct
    public void init() throws Exception {
        DescriptiveStatistics generalLatencyStats = new DescriptiveStatistics();

        subscriberService.startSubscribers(subscriberGroupsConfiguration, msgByteBuf -> {
            long now = System.currentTimeMillis();
            byte[] mqttMessageBytes = toBytes(msgByteBuf);
            Message message = mapper.readValue(mqttMessageBytes, Message.class);
            generalLatencyStats.addValue(now - message.getCreateTime());
        });

        publisherService.connectPublishers(publisherGroupsConfiguration);

        mockClientService.connectMockClients(MOCK_CLIENTS);

        publisherService.startPublishing(TOTAL_PUBLISHER_MESSAGES, MAX_MSGS_PER_PUBLISHER_PER_SECOND);


        Thread.sleep(TimeUnit.SECONDS.toMillis(SECONDS_TO_RUN + ADDITIONAL_SECONDS_TO_WAIT));

        subscriberService.disconnectSubscribers();
        publisherService.disconnectPublishers();
        mockClientService.disconnectMockClients();

        SubscriberAnalysisResult analysisResult = subscriberService.analyzeReceivedMessages(publisherGroupsConfiguration, TOTAL_PUBLISHER_MESSAGES);

        log.info("Latency stats: avg - {}, median - {}, max - {}, min - {}, 95th - {}, lost messages - {}, duplicated messages - {}, total received messages - {}.",
                generalLatencyStats.getSum() / generalLatencyStats.getN(),
                generalLatencyStats.getMean(), generalLatencyStats.getMax(),
                generalLatencyStats.getMin(), generalLatencyStats.getPercentile(95),
                analysisResult.getLostMessages(), analysisResult.getDuplicatedMessages(),
                generalLatencyStats.getN());



        int totalPublishers = publisherGroupsConfiguration.stream().mapToInt(PublisherGroup::getPublishers).sum();
        int totalSubscribers = subscriberGroupsConfiguration.stream().mapToInt(SubscriberGroup::getSubscribers).sum();
        int totalPublishedMessages = totalPublishers * TOTAL_PUBLISHER_MESSAGES;
        log.info("Test run info: publishers - {}, subscribers - {}, dummy client connections - {}, max messages per second - {}, " +
                "run time - {}s, total published messages - {}, expected total received messages - {}",
                totalPublishers, totalSubscribers, MOCK_CLIENTS, MAX_MSGS_PER_PUBLISHER_PER_SECOND,
                SECONDS_TO_RUN, totalPublishedMessages, analysisResult.getExpectedTotalReceivedMessages());
    }


    private static byte[] toBytes(ByteBuf inbound) {
        byte[] bytes = new byte[inbound.readableBytes()];
        int readerIndex = inbound.readerIndex();
        inbound.getBytes(readerIndex, bytes);
        return bytes;
    }
}
