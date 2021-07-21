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
package org.thingsboard.mqtt.broker.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.data.NodeInfo;
import org.thingsboard.mqtt.broker.service.orchestration.TestRestService;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RestController
@Profile("orchestrator")
@RequiredArgsConstructor
@RequestMapping(ClusterConst.ORCHESTRATOR_PATH)
public class OrchestratorController {

    private final TestRestService testRestService;

    @Value("${test-run.max-cluster-wait-time}")
    private int waitTime;

    private final Set<String> nodeUrls;
    private final AtomicLong lastNodeReady = new AtomicLong(0);

    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public String nodeReady(@RequestBody NodeInfo nodeInfo) {
        log.info("Received node info - {}", nodeInfo);
        long now = System.currentTimeMillis();
        if (lastNodeReady.getAndSet(now) < now - TimeUnit.SECONDS.toMillis(waitTime)) {
            log.info("Clearing {} waiting nodes.", nodeUrls.size());
            nodeUrls.clear();
        }
        nodeUrls.add(nodeInfo.getNodeUrl());
        if (nodeUrls.size() == nodeInfo.getNodesInCluster()) {
            for (String nodeUrl : nodeUrls) {
                log.info("Notifying {} node.", nodeUrl);
                try {
                    testRestService.notifyClusterIsReady(nodeUrl);
                } catch (Exception e) {
                    log.warn("Failed to notify {} node", nodeUrl);
                }
            }
            nodeUrls.clear();
        }
        return "OK";
    }
}
