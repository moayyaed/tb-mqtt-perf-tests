package org.thingsboard.mqtt.broker.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttQoS;
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
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class MqttPerformanceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private static final List<PublisherGroup> publisherGroupsConfiguration = Arrays.asList(
            new PublisherGroup(1, 200, "europe/ua/kyiv/tb/"),
            new PublisherGroup(2, 100, "europe/ua/kyiv/"),
            new PublisherGroup(3, 100, "asia/")
//            new PublisherGroup(4, 1, "perf/test/topic/", "perf_test_publisher_")
    );

    private static final Set<Integer> ALL_PUBLISHER_IDS = publisherGroupsConfiguration.stream().map(PublisherGroup::getId).collect(Collectors.toSet());

    private static final List<SubscriberGroup> subscriberGroupsConfiguration = Arrays.asList(
//            new SubscriberGroup(1, 50, "europe/ua/kyiv/tb/+", Set.of(1), null),
//            new SubscriberGroup(2, 50, "europe/ua/kyiv/#", Set.of(1, 2), null),
            new SubscriberGroup(3, 30, "#", ALL_PUBLISHER_IDS, new PersistentSessionInfo(PersistentClientType.APPLICATION))
//            new SubscriberGroup(4, 20, "europe/ua/kyiv/tb/#", Set.of(1), new PersistentSessionInfo(PersistentClientType.DEVICE)),
//            new SubscriberGroup(5, 10, "europe/ua/kyiv/#", Set.of(1, 2), new PersistentSessionInfo(PersistentClientType.DEVICE))
//            new SubscriberGroup(6, 1, "europe/ua/kyiv/#", Set.of(1, 2), new PersistentSessionInfo(PersistentClientType.APPLICATION))
//            new SubscriberGroup(7, 1, "perf/test/topic/+", Set.of(4), null, "perf_test_basic_")
//            new SubscriberGroup(8, 1, "perf/test/topic/+", Set.of(4), new PersistentSessionInfo(PersistentClientType.DEVICE), "perf_test_device_")
//            new SubscriberGroup(9, 1, "perf/test/topic/+", Set.of(4), new PersistentSessionInfo(PersistentClientType.APPLICATION), "perf_test_application_")
            );

    private static final int SECONDS_TO_RUN = 30;
    private static final int ADDITIONAL_SECONDS_TO_WAIT = 30;

    private static final int MOCK_CLIENTS = 1000;
    private static final int MAX_MSGS_PER_PUBLISHER_PER_SECOND = 1;

    private static final MqttQoS PUBLISHER_QOS = MqttQoS.AT_LEAST_ONCE;
    private static final MqttQoS SUBSCRIBER_QOS = MqttQoS.AT_LEAST_ONCE;

    private static final int TOTAL_PUBLISHER_MESSAGES = SECONDS_TO_RUN * MAX_MSGS_PER_PUBLISHER_PER_SECOND;

    private final MockClientService mockClientService;
    private final SubscriberService subscriberService;
    private final PublisherService publisherService;
    private final PersistedMqttClientService persistedMqttClientService;

    @PostConstruct
    public void init() throws Exception {
        log.info("Start performance test.");
        printTestRunConfiguration();

        DescriptiveStatistics generalLatencyStats = new DescriptiveStatistics();

        persistedMqttClientService.clearPersistedSessions(subscriberGroupsConfiguration);
        persistedMqttClientService.removeApplicationClients(subscriberGroupsConfiguration);
        persistedMqttClientService.initApplicationClients(subscriberGroupsConfiguration);

        subscriberService.startSubscribers(subscriberGroupsConfiguration, SUBSCRIBER_QOS, msgByteBuf -> {
            long now = System.currentTimeMillis();
            byte[] mqttMessageBytes = toBytes(msgByteBuf);
            Message message = mapper.readValue(mqttMessageBytes, Message.class);
            generalLatencyStats.addValue(now - message.getCreateTime());
        });

        publisherService.connectPublishers(publisherGroupsConfiguration);

        mockClientService.connectMockClients(MOCK_CLIENTS);

        publisherService.startPublishing(TOTAL_PUBLISHER_MESSAGES, MAX_MSGS_PER_PUBLISHER_PER_SECOND, PUBLISHER_QOS);


        Thread.sleep(TimeUnit.SECONDS.toMillis(SECONDS_TO_RUN + ADDITIONAL_SECONDS_TO_WAIT));

        subscriberService.disconnectSubscribers();
        publisherService.disconnectPublishers();
        mockClientService.disconnectMockClients();

        // wait for all MQTT clients to close
        Thread.sleep(1000);

        persistedMqttClientService.clearPersistedSessions(subscriberGroupsConfiguration);

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
                        "'APPLICATION' persistent subscribers - {}, dummy client connections - {}," +
                        "publisher QoS - {}, subscriber QoS - {}, max messages per second - {}, " +
                        "run time - {}s, total published messages - {}, expected total received messages - {}",
                totalPublishers, nonPersistedSubscribers, persistedDevicesSubscribers,
                persistedApplicationsSubscribers, MOCK_CLIENTS,
                PUBLISHER_QOS, SUBSCRIBER_QOS, MAX_MSGS_PER_PUBLISHER_PER_SECOND,
                SECONDS_TO_RUN, totalPublishedMessages, totalExpectedReceivedMessages);
    }


    private static byte[] toBytes(ByteBuf inbound) {
        byte[] bytes = new byte[inbound.readableBytes()];
        int readerIndex = inbound.readerIndex();
        inbound.getBytes(readerIndex, bytes);
        return bytes;
    }
}
