#!/bin/bash
for i in {0..79}
do
   kubectl exec broker-tests-$i -- sh -c "export TEST_RUN_TEST_NODE_URL=http://broker-tests-$i.broker-tests.thingsboard-mqtt-broker.svc.cluster.local:8088;export TEST_RUN_SEQUENTIAL_NUMBER=$i;export TEST_RUN_PARALLEL_TESTS_COUNT=80;export TEST_RUN_TEST_ORCHESTRATOR_URL=http://broker-tests-orchestrator-0.broker-tests-orchestrator.thingsboard-mqtt-broker.svc.cluster.local:8088;start-tb-mqtt-broker-performance-tests.sh;" > broker-tests-$i.log 2>&1 &
done
