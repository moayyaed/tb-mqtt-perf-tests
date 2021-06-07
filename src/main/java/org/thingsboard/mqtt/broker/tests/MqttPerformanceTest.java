package org.thingsboard.mqtt.broker.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.data.Message;
import org.thingsboard.mqtt.broker.data.PersistentClientType;
import org.thingsboard.mqtt.broker.data.PersistentSessionInfo;
import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.SubscriberAnalysisResult;
import org.thingsboard.mqtt.broker.data.SubscriberGroup;
import org.thingsboard.mqtt.broker.service.MockClientService;
import org.thingsboard.mqtt.broker.service.PersistedMqttClientService;
import org.thingsboard.mqtt.broker.service.PublisherService;
import org.thingsboard.mqtt.broker.service.SubscriberService;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class MqttPerformanceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private static final List<PublisherGroup> publisherGroupsConfiguration = Arrays.asList(
            new PublisherGroup(1, 450, "europe/ua/kyiv/tb/"),
            new PublisherGroup(2, 100, "europe/ua/kyiv/"),
            new PublisherGroup(3, 50, "asia/")
    );

    private static final List<SubscriberGroup> subscriberGroupsConfiguration = Arrays.asList(
            new SubscriberGroup(1, 100, "europe/ua/kyiv/tb/+", Set.of(1), null),
            new SubscriberGroup(2, 50, "europe/ua/kyiv/#", Set.of(1, 2), null),
            new SubscriberGroup(3, 10, "#", Set.of(1, 2, 3), new PersistentSessionInfo(PersistentClientType.APPLICATION))
//            new SubscriberGroup(4, 20, "europe/ua/kyiv/tb/#", Set.of(1), new PersistentSessionInfo(PersistentClientType.DEVICE)),
//            new SubscriberGroup(5, 20, "europe/ua/kyiv/#", Set.of(1, 2), new PersistentSessionInfo(PersistentClientType.DEVICE))
    );

    private static final int SECONDS_TO_RUN = 30;
    private static final int ADDITIONAL_SECONDS_TO_WAIT = 15;

    private static final int MOCK_CLIENTS = 0;
    private static final int MAX_MSGS_PER_PUBLISHER_PER_SECOND = 5;

    private static final int TOTAL_PUBLISHER_MESSAGES = SECONDS_TO_RUN * MAX_MSGS_PER_PUBLISHER_PER_SECOND;

    private final MockClientService mockClientService;
    private final SubscriberService subscriberService;
    private final PublisherService publisherService;
    private final PersistedMqttClientService persistedMqttClientService;

    @PostConstruct
    public void init() throws Exception {
        printTestRunConfiguration();

        DescriptiveStatistics generalLatencyStats = new DescriptiveStatistics();

        persistedMqttClientService.initApplicationClients(subscriberGroupsConfiguration);

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

        // wait for all MQTT clients to close
        Thread.sleep(1000);

        persistedMqttClientService.clearPersistedSessions(subscriberGroupsConfiguration);
        persistedMqttClientService.removeApplicationClients(subscriberGroupsConfiguration);

        SubscriberAnalysisResult analysisResult = subscriberService.analyzeReceivedMessages(publisherGroupsConfiguration, TOTAL_PUBLISHER_MESSAGES);

        log.info("Latency stats: avg - {}, median - {}, max - {}, min - {}, 95th - {}, lost messages - {}, duplicated messages - {}, total received messages - {}.",
                generalLatencyStats.getSum() / generalLatencyStats.getN(),
                generalLatencyStats.getMean(), generalLatencyStats.getMax(),
                generalLatencyStats.getMin(), generalLatencyStats.getPercentile(95),
                analysisResult.getLostMessages(), analysisResult.getDuplicatedMessages(),
                generalLatencyStats.getN());

        // wait for all MQTT clients to close
        Thread.sleep(1000);
    }

    private void printTestRunConfiguration() {
        int totalPublishers = publisherGroupsConfiguration.stream().mapToInt(PublisherGroup::getPublishers).sum();
        int nonPersistedSubscribers = subscriberGroupsConfiguration.stream()
                .filter(subscriberGroup -> subscriberGroup.getPersistentSessionInfo() == null)
                .mapToInt(SubscriberGroup::getSubscribers)
                .sum();
        int persistedApplicationsSubscribers = subscriberGroupsConfiguration.stream()
                .filter(subscriberGroup -> subscriberGroup.getPersistentSessionInfo() != null
                        && subscriberGroup.getPersistentSessionInfo().getClientType() == PersistentClientType.APPLICATION)
                .mapToInt(SubscriberGroup::getSubscribers)
                .sum();
        int persistedDevicesSubscribers = subscriberGroupsConfiguration.stream()
                .filter(subscriberGroup -> subscriberGroup.getPersistentSessionInfo() != null
                        && subscriberGroup.getPersistentSessionInfo().getClientType() == PersistentClientType.DEVICE)
                .mapToInt(SubscriberGroup::getSubscribers)
                .sum();
        int totalPublishedMessages = totalPublishers * TOTAL_PUBLISHER_MESSAGES;
        int totalExpectedReceivedMessages = subscriberService.calculateTotalExpectedReceivedMessages(subscriberGroupsConfiguration, publisherGroupsConfiguration, TOTAL_PUBLISHER_MESSAGES);
        log.info("Test run info: publishers - {}, non-persistent subscribers - {}, regular persistent subscribers - {}, " +
                        "'APPLICATION' persistent subscribers - {}, dummy client connections - {}, max messages per second - {}, " +
                        "run time - {}s, total published messages - {}, expected total received messages - {}",
                totalPublishers, nonPersistedSubscribers, persistedDevicesSubscribers,
                persistedApplicationsSubscribers, MOCK_CLIENTS, MAX_MSGS_PER_PUBLISHER_PER_SECOND,
                SECONDS_TO_RUN, totalPublishedMessages, totalExpectedReceivedMessages);
    }


    private static byte[] toBytes(ByteBuf inbound) {
        byte[] bytes = new byte[inbound.readableBytes()];
        int readerIndex = inbound.readerIndex();
        inbound.getBytes(readerIndex, bytes);
        return bytes;
    }
}
