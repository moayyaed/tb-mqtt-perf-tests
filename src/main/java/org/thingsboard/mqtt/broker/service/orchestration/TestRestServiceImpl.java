package org.thingsboard.mqtt.broker.service.orchestration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.mqtt.broker.config.TestRunClusterConfig;
import org.thingsboard.mqtt.broker.controller.ClusterConst;
import org.thingsboard.mqtt.broker.data.NodeInfo;

import javax.annotation.PostConstruct;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestRestServiceImpl implements TestRestService {

    private final TestRunClusterConfig testRunClusterConfig;
    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${test-run.orchestrator-url}")
    private String orchestratorUrl;
    @Value("${test-run.node-url}")
    private String nodeUrl;

    private RestTemplate restTemplate;

    @PostConstruct
    public void init() {
        this.restTemplate = restTemplateBuilder.build();
    }

    @Override
    public void notifyClusterIsReady(String nodeUrl) {
        restTemplate.postForLocation(nodeUrl, "OK");
    }

    @Override
    public boolean notifyNodeIsReady() {
        if (StringUtils.isEmpty(orchestratorUrl) || StringUtils.isEmpty(nodeUrl)) {
            return false;
        }
        try {
            return restTemplate.postForLocation(orchestratorUrl + ClusterConst.ORCHESTRATOR_PATH,
                    new NodeInfo(nodeUrl + ClusterConst.NODE_PATH, testRunClusterConfig.getParallelTestsCount())) != null;
        } catch (Exception e) {
            log.error("Error notifying orchestrator", e);
            return false;
        }
    }
}
