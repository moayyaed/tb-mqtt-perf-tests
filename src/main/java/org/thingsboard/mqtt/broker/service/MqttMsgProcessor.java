package org.thingsboard.mqtt.broker.service;

import io.netty.buffer.ByteBuf;

@FunctionalInterface
public interface MqttMsgProcessor {
    void process(ByteBuf msg) throws Exception;
}
