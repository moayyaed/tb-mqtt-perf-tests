/**
 * Copyright © 2016-2021 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.client.mqtt.MqttClient;
import org.thingsboard.mqtt.broker.client.mqtt.MqttClientConfig;
import org.thingsboard.mqtt.broker.client.mqtt.MqttConnectResult;
import org.thingsboard.mqtt.broker.client.mqtt.MqttHandler;
import org.thingsboard.mqtt.broker.client.mqtt.ReceivedMsgProcessor;
import org.thingsboard.mqtt.broker.data.dto.HostPortDto;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientInitializerImpl implements ClientInitializer {

    private final HostPortService hostPortService;
    private final SslConfig sslConfig;
    private final ReceivedMsgProcessor receivedMsgProcessor;


    @Value("${mqtt.client.connect-timeout-seconds:5}")
    private int connectTimeout;

    private EventLoopGroup EVENT_LOOP_GROUP;

    @PostConstruct
    public void init() {
        EVENT_LOOP_GROUP = new NioEventLoopGroup();
    }

    @Override
    public MqttClient createClient(String clientId) {
        return createClient(clientId, true, null);
    }

    @Override
    public MqttClient createClient(String clientId, boolean cleanSession) {
        return createClient(clientId, cleanSession, null);
    }

    @Override
    public MqttClient createClient(String clientId, boolean cleanSession, MqttHandler defaultHandler) {
        MqttClientConfig config = new MqttClientConfig(sslConfig.getSslContext());
        config.setClientId(clientId);
        config.setCleanSession(cleanSession);
        config.setProtocolVersion(MqttVersion.MQTT_3_1_1);
        MqttClient client = MqttClient.create(config, defaultHandler, receivedMsgProcessor);
        client.setEventLoop(EVENT_LOOP_GROUP);
        return client;
    }

    @Override
    public Future<MqttConnectResult> connectClient(MqttClient client) {
        HostPortDto hostPort = hostPortService.getHostPort();
        return client.connect(hostPort.getHost(), hostPort.getPort());
    }

    @Override
    // TODO: remove this
    public MqttClient createAndConnectClient(String clientId, boolean cleanSession, MqttHandler defaultHandler) {
        MqttClient client = createClient(clientId, cleanSession, defaultHandler);
        MqttConnectResult result;
        Future<MqttConnectResult> connectFuture = connectClient(client);
        try {
            result = connectFuture.get(connectTimeout, TimeUnit.SECONDS);
        } catch (Exception ex) {
            connectFuture.cancel(true);
            client.disconnect();
            log.error("[{}] Failed to connect to MQTT Broker", clientId, ex);
            throw new RuntimeException("Failed to connect to MQTT broker with client " + clientId);
        }
        if (!result.isSuccess()) {
            connectFuture.cancel(true);
            client.disconnect();
            throw new RuntimeException(String.format("Failed to connect to MQTT broker with client %s. Result code is: %s",
                    clientId, result.getReturnCode()));
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
