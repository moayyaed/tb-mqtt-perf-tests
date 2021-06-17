#!/bin/bash
#
# Copyright © 2016-2018 The Thingsboard Authors
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

source .env

set -e

kubectl apply -f config/tb-broker-namespace.yml
kubectl config set-context $(kubectl config current-context) --namespace=thingsboard-mqtt-broker

cp $TEST_RUN_CONFIG_FILE test-config/test_run_config.json
kubectl delete configmap test-run-config
kubectl create configmap test-run-config --from-file=test-config/test_run_config.json --from-file=test-config/logback.xml --from-file=test-config/tb-mqtt-broker-performance-tests.conf
rm test-config/test_run_config.json

kubectl apply -f config/tests.yml &&
kubectl wait --for=condition=Ready pod/tests --timeout=120s &&
kubectl exec tests -- sh -c 'start-tb-mqtt-broker-performance-tests.sh; touch /tmp/test-finished;'

kubectl delete pod tests
