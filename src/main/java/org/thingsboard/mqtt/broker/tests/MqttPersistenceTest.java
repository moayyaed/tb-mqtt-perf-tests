package org.thingsboard.mqtt.broker.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.data.PersistentClientType;
import org.thingsboard.mqtt.broker.data.PersistentSessionInfo;
import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.SubscriberGroup;
import org.thingsboard.mqtt.broker.service.PersistedMqttClientService;
import org.thingsboard.mqtt.broker.service.PublisherService;
import org.thingsboard.mqtt.broker.service.SubscriberService;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@ConditionalOnProperty(prefix = "persistence-test", value = "enabled", havingValue = "true")
@Component
@Slf4j
@RequiredArgsConstructor
public class MqttPersistenceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private static final List<PublisherGroup> publisherGroupsConfiguration = Arrays.asList(
            new PublisherGroup(1, 50, "europe/ua/kyiv/tb/"),
            new PublisherGroup(2, 50, "europe/ua/kyiv/"),
            new PublisherGroup(3, 50, "asia/")
    );

    private static final List<SubscriberGroup> subscriberGroupsConfiguration = Arrays.asList(
//            new SubscriberGroup(3, 5, "#", Set.of(1, 2, 3), new PersistentSessionInfo(PersistentClientType.APPLICATION)),
            new SubscriberGroup(4, 20, "europe/ua/kyiv/tb/#", Set.of(1), new PersistentSessionInfo(PersistentClientType.DEVICE)),
            new SubscriberGroup(5, 20, "europe/ua/kyiv/#", Set.of(1, 2), new PersistentSessionInfo(PersistentClientType.DEVICE))
    );


    private static final int TOTAL_PUBLISHER_MESSAGES = 100;
    private static final int MAX_MSGS_PER_PUBLISHER_PER_SECOND = 10;

    private static final int SECONDS_TO_WAIT = TOTAL_PUBLISHER_MESSAGES / MAX_MSGS_PER_PUBLISHER_PER_SECOND;
    private static final int ADDITIONAL_SECONDS_TO_WAIT = 10;

    private final SubscriberService subscriberService;
    private final PublisherService publisherService;
    private final PersistedMqttClientService persistedMqttClientService;

    @PostConstruct
    public void init() throws Exception {
        log.info("Start persistence test.");
        persistedMqttClientService.clearPersistedSessions(subscriberGroupsConfiguration);
        persistedMqttClientService.initApplicationClients(subscriberGroupsConfiguration);

        subscriberService.startSubscribers(subscriberGroupsConfiguration, MqttQoS.AT_LEAST_ONCE, msgByteBuf -> {});
        subscriberService.disconnectSubscribers();


        publisherService.connectPublishers(publisherGroupsConfiguration);
        publisherService.startPublishing(TOTAL_PUBLISHER_MESSAGES, MAX_MSGS_PER_PUBLISHER_PER_SECOND, MqttQoS.EXACTLY_ONCE);

        Thread.sleep(TimeUnit.SECONDS.toMillis(SECONDS_TO_WAIT + ADDITIONAL_SECONDS_TO_WAIT));

        publisherService.disconnectPublishers();

        AtomicLong totalReceivedMessages = new AtomicLong(0);
        subscriberService.startSubscribers(subscriberGroupsConfiguration, MqttQoS.AT_LEAST_ONCE, msgByteBuf -> totalReceivedMessages.incrementAndGet());

        Thread.sleep(TimeUnit.SECONDS.toMillis(SECONDS_TO_WAIT + ADDITIONAL_SECONDS_TO_WAIT));

        subscriberService.disconnectSubscribers();

        persistedMqttClientService.clearPersistedSessions(subscriberGroupsConfiguration);

        int totalExpectedReceivedMessages = subscriberService.calculateTotalExpectedReceivedMessages(subscriberGroupsConfiguration, publisherGroupsConfiguration, TOTAL_PUBLISHER_MESSAGES);

        log.info("Expected messages - {}, received messages - {}", totalExpectedReceivedMessages, totalReceivedMessages.get());
    }
}
