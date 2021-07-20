package org.thingsboard.mqtt.broker.service.orchestration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ClusterSynchronizerImpl implements ClusterSynchronizer {

    private final CountDownLatch latch = new CountDownLatch(1);

    @Value("${test-run.max-cluster-wait-time}")
    private int waitTime;

    @Override
    public void notifyClusterReady() {
        latch.countDown();
    }

    @Override
    public void awaitClusterReady() {
        log.info("Waiting for cluster to be ready");
        try {
            latch.await(waitTime, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.warn("Failed to wait for cluster to be ready.");
        }
    }
}
