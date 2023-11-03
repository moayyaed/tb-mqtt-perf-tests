#!/bin/bash
#
# Copyright © 2016-2023 The Thingsboard Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

for i in {0..5}
do
   kubectl exec broker-tests-subscribers-$i -- sh -c "export TEST_RUN_TEST_NODE_URL=http://broker-tests-subscribers-$i.broker-tests-subscribers.thingsboard-mqtt-broker.svc.cluster.local:8088;export TEST_RUN_SEQUENTIAL_NUMBER=$i;export TEST_RUN_PARALLEL_TESTS_COUNT=6;export TEST_RUN_TEST_ORCHESTRATOR_URL=http://broker-tests-orchestrator-0.broker-tests-orchestrator.thingsboard-mqtt-broker.svc.cluster.local:8088;start-tb-mqtt-broker-performance-tests.sh;" > broker-tests-subscribers-$i.log 2>&1 &
done

kubectl exec broker-tests-publishers-0 -- sh -c "export TEST_RUN_TEST_NODE_URL=http://broker-tests-publishers-0.broker-tests-publishers.thingsboard-mqtt-broker.svc.cluster.local:8088;export TEST_RUN_SEQUENTIAL_NUMBER=0;export TEST_RUN_PARALLEL_TESTS_COUNT=1;export TEST_RUN_TEST_ORCHESTRATOR_URL=http://broker-tests-orchestrator-0.broker-tests-orchestrator.thingsboard-mqtt-broker.svc.cluster.local:8088;start-tb-mqtt-broker-performance-tests.sh;" > broker-tests-publishers-0.log 2>&1 &
