package org.thingsboard.mqtt.broker.service.orchestration;

public interface ClusterSynchronizer {
    void notifyClusterReady();

    void awaitClusterReady();
}
