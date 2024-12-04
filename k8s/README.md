To configure broker tests:

```
./k8s-deploy-broker-tests.sh
```

To delete broker tests:

```
./k8s-delete-broker-tests.sh
```

Once each pod is deployed, it will start the test automatically.
There is no need to run `start-test.sh`.

Unlike other MQTT test scenarios, the [Point-to-point](https://docs.aws.amazon.com/whitepapers/latest/designing-mqtt-topics-aws-iot-core/mqtt-communication-patterns.html#point-to-point) 
(P2P) communication tests involve a large number of publishers and subscribers. As a result, using a static configuration file to define publisher and subscriber groups becomes cumbersome.

To address this, we have updated our testing approach to auto-generate publisher and subscriber configurations when no configuration file is provided. 

```yaml
# If the configuration file is not set, the default configuration (p2p) will be used.
# "p2p" or "point-to-point" refers to a 1 publisher to 1 subscriber configuration.
# Use TEST_RUN_DEFAULT_PUB_SUB_GROUPS_COUNT to define the number of p2p groups.
configuration-file: "${TEST_RUN_CONFIGURATION_FILE:}"
```

By default, the tool will use a "point-to-point" (P2P) configuration, meaning each publisher is paired with one subscriber.
If no configuration file is set, the following default behavior will be applied:

```yaml
test-run:
  default:
    worker-type: "${TEST_RUN_DEFAULT_WORKER_TYPE:DEFAULT}" # DEFAULT, PUBLISHER, SUBSCRIBER
    pub-sub-groups-count: "${TEST_RUN_DEFAULT_PUB_SUB_GROUPS_COUNT:100}"
    seconds-to-run: "${TEST_RUN_DEFAULT_SECONDS_TO_RUN:30}"
    additional-seconds-to-wait: "${TEST_RUN_DEFAULT_ADDITIONAL_SECONDS_TO_WAIT:10}"
    dummy-clients: "${TEST_RUN_DEFAULT_DUMMY_CLIENTS:0}"
    max-msgs-per-publisher-per-second: "${TEST_RUN_DEFAULT_MAX_MSGS_PER_PUBLISHER_PER_SECOND:1}"
    publisher-qos: "${TEST_RUN_DEFAULT_PUBLISHER_QOS:0}"
    subscriber-qos: "${TEST_RUN_DEFAULT_SUBSCRIBER_QOS:0}"
    min-payload-size: "${TEST_RUN_DEFAULT_MIN_PAYLOAD_SIZE:256}"
    max-concurrent-operations: "${TEST_RUN_DEFAULT_MAX_CONCURRENT_OPERATIONS:1000}"
```


**Note:** In this case json test configuration `test_run_config.json` inside
the [broker-tests-publishers-config.yml](broker-tests-publishers-config.yml) config file
or [broker-tests-subscribers-config.yml](broker-tests-subscribers-config.yml) config file is empty:

```yaml
  test_run_config.json: |
    {
    }
```

However, you can still use config files mentioned above to update other settings such as the Java options
in the `tb-mqtt-broker-performance-tests.conf` section or logging settings in the `logback.xml` file section.

You can find override environment variables for publishers and subscribers in the next configuration files:

- [broker-tests-publishers.yml](broker-tests-publishers.yml)
- [broker-tests-subscribers.yml](broker-tests-subscribers.yml)

**Note:** Please pay attention to the environment variables that start with the `TEST_RUN_DEFAULT_` prefix.

