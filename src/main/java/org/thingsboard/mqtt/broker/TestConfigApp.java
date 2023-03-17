/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Paths;

/**
 * This simple class is used to generate configuration test file
 */
@Slf4j
public class TestConfigApp {

    private static final String SRC_DIR = "src";
    private static final String MAIN_DIR = "main";
    private static final String RESOURCES_DIR = "resources";
    private static final String FILE_NAME = "test.json";

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();

        ArrayNode publisherGroups = createPublishersConfig(mapper);
        result.set("publisherGroups", publisherGroups);

        ArrayNode subscriberGroups = createSubscribersConfig(mapper);
        result.set("subscriberGroups", subscriberGroups);

        addGeneralConfig(mapper, result);

        String workDir = System.getProperty("user.dir");
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(Paths.get(workDir, SRC_DIR, MAIN_DIR, RESOURCES_DIR, FILE_NAME).toFile(), result);
    }

    private static void addGeneralConfig(ObjectMapper mapper, ObjectNode result) {
        result.put("dummyClients", 0);
        result.put("secondsToRun", 300);
        result.put("additionalSecondsToWait", 5);
        result.put("maxMsgsPerPublisherPerSecond", 4);
        result.put("publisherQosValue", 1);
        result.put("subscriberQosValue", 1);
        result.put("minPayloadSize", 1);
        result.put("maxConcurrentOperations", 10000);
        ArrayNode telemetryKeys = mapper.createArrayNode().add("lat").add("long").add("speed").add("fuel").add("batLvl");
        result.set("telemetryKeys", telemetryKeys);
    }

    private static ArrayNode createSubscribersConfig(ObjectMapper mapper) {
        ArrayNode subscriberGroups = mapper.createArrayNode();
        for (int i = 1; i <= 100; i++) {
            ObjectNode subscriberGroup = mapper.createObjectNode();

            subscriberGroup.put("id", i);
            subscriberGroup.put("subscribers", 1);
            subscriberGroup.put("topicFilter", "test/topic/" + i + "/+");
            ArrayNode expectedPublisherGroups = mapper.createArrayNode();
            expectedPublisherGroups.add(i);
            subscriberGroup.set("expectedPublisherGroups", expectedPublisherGroups);
            subscriberGroup.set("persistentSessionInfo", null);
            subscriberGroup.set("clientIdPrefix", null);

            subscriberGroups.add(subscriberGroup);
        }
        return subscriberGroups;
    }

    private static ArrayNode createPublishersConfig(ObjectMapper mapper) {
        ArrayNode publisherGroups = mapper.createArrayNode();
        for (int i = 1; i <= 100; i++) {
            ObjectNode publisherGroup = mapper.createObjectNode();

            publisherGroup.put("id", i);
            publisherGroup.put("publishers", 1);
            publisherGroup.put("topicPrefix", "test/topic/" + i + "/");
            publisherGroup.set("clientIdPrefix", null);

            publisherGroups.add(publisherGroup);
        }
        return publisherGroups;
    }

}
