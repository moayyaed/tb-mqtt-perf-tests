To configure test-run properties:

```
kubectl apply -f config/mqtt-broker-test-run-config.yml
```

To set up test pods:

```
kubectl apply -f config/broker-tests.yml
kubectl rollout status statefulset/broker-tests
```

To run tests:
**Note:** you have to run `kubectl exec ...` command for every broker-tests pod.

```
kubectl exec broker-tests-0 -- sh -c 'export TEST_RUN_SEQUENTIAL_NUMBER=0;export TEST_RUN_PARALLEL_TESTS_COUNT=1;start-tb-mqtt-broker-performance-tests.sh;' > broker-tests-0.log 2>&1 &
```


```
kubectl exec broker-tests-0 -- sh -c 'export TEST_RUN_SEQUENTIAL_NUMBER=0;export TEST_RUN_PARALLEL_TESTS_COUNT=2;start-tb-mqtt-broker-performance-tests.sh;' > broker-tests-0.log 2>&1 &
kubectl exec broker-tests-1 -- sh -c 'export TEST_RUN_SEQUENTIAL_NUMBER=1;export TEST_RUN_PARALLEL_TESTS_COUNT=2;start-tb-mqtt-broker-performance-tests.sh;' > broker-tests-1.log 2>&1 &
```


**Note:** you can change test run configuration in `test_run_config.json` file inside of the `config/mqtt-broker-test-run-config.yml` config. 