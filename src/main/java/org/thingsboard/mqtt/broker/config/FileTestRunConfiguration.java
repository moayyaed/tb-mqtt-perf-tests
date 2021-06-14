package org.thingsboard.mqtt.broker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.SubscriberGroup;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${test-run-configuration-file:}'!=''")
public class FileTestRunConfiguration implements TestRunConfiguration {
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${test-run-configuration-file}")
    private String configurationFilePath;

    private File configurationFile;
    private TestRunConfigurationInfo testRunConfigurationInfo;

    @PostConstruct
    public void init() throws Exception {
        this.configurationFile = new File(configurationFilePath);
        this.testRunConfigurationInfo = mapper.readValue(configurationFile, TestRunConfigurationInfo.class);
    }

    @Override
    public String getConfigurationName() {
        return configurationFile.getName();
    }

    @Override
    public List<SubscriberGroup> getSubscribersConfig() {
        return testRunConfigurationInfo.getSubscriberGroups();
    }

    @Override
    public List<PublisherGroup> getPublishersConfig() {
        return testRunConfigurationInfo.getPublisherGroups();
    }

    @Override
    public int getMaxMessagesPerPublisherPerSecond() {
        return testRunConfigurationInfo.getMaxMsgsPerPublisherPerSecond();
    }

    @Override
    public int getSecondsToRun() {
        return testRunConfigurationInfo.getSecondsToRun();
    }

    @Override
    public int getAdditionalSecondsToWait() {
        return testRunConfigurationInfo.getAdditionalSecondsToWait();
    }

    @Override
    public int getTotalPublisherMessagesCount() {
        return getMaxMessagesPerPublisherPerSecond() * getSecondsToRun();
    }

    @Override
    public int getNumberOfDummyClients() {
        return testRunConfigurationInfo.getDummyClients();
    }

    @Override
    public MqttQoS getPublisherQoS() {
        return MqttQoS.valueOf(testRunConfigurationInfo.getPublisherQosValue());
    }

    @Override
    public MqttQoS getSubscriberQoS() {
        return MqttQoS.valueOf(testRunConfigurationInfo.getSubscriberQosValue());
    }
}
