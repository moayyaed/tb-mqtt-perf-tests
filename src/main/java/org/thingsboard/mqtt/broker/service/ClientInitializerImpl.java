package org.thingsboard.mqtt.broker.service;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.MqttClientConfig;
import org.thingsboard.mqtt.MqttConnectResult;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ClientInitializerImpl implements ClientInitializer {

    @Value("${mqtt.host}")
    private String mqttHost;
    @Value("${mqtt.port}")
    private int mqttPort;
    @Value("${mqtt.client.connect-timeout-seconds:5}")
    private int connectTimeout;

    private EventLoopGroup EVENT_LOOP_GROUP;

    @PostConstruct
    public void init() {
        EVENT_LOOP_GROUP = new NioEventLoopGroup();
    }

    @Override
    public MqttClient initClient(String clientId) {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(clientId);
        MqttClient client = MqttClient.create(config, null);
        client.setEventLoop(EVENT_LOOP_GROUP);
        Future<MqttConnectResult> connectFuture = client.connect(mqttHost, mqttPort);
        MqttConnectResult result;
        try {
            result = connectFuture.get(connectTimeout, TimeUnit.SECONDS);
        } catch (Exception ex) {
            connectFuture.cancel(true);
            client.disconnect();
            throw new RuntimeException(String.format("Failed to connect to MQTT broker at %s:%d with client %s.",
                    mqttHost, mqttPort, clientId));
        }
        if (!result.isSuccess()) {
            connectFuture.cancel(true);
            client.disconnect();
            throw new RuntimeException(String.format("Failed to connect to MQTT broker at %s:%d with client %s. Result code is: %s",
                    mqttHost, mqttPort, clientId, result.getReturnCode()));
        }
        return client;
    }


    @PreDestroy
    public void destroy() {
        if (!EVENT_LOOP_GROUP.isShutdown()) {
            EVENT_LOOP_GROUP.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
    }
}
