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
package org.thingsboard.mqtt.broker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.data.PublisherGroup;
import org.thingsboard.mqtt.broker.data.SubscriberGroup;

@Service
@RequiredArgsConstructor
public class ClientIdServiceImpl implements ClientIdService {

    @Override
    public String createSubscriberClientId(SubscriberGroup subscriberGroup, int subscriberId) {
        return subscriberGroup.getClientIdPrefix() + subscriberId;
    }

    @Override
    public String createPublisherClientId(PublisherGroup publisherGroup, int publisherId) {
        return publisherGroup.getClientIdPrefix() + publisherId;
    }

    @Override
    public String createDummyClientId(int dummyId) {
        return  "test_dummy_client_" + dummyId;
    }
}
