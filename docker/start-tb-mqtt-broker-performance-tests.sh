#!/bin/bash
#
# Copyright © 2016-2022 The Thingsboard Authors
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

jarfile=${pkg.installFolder}/bin/${pkg.name}.jar

configfile="/config/${pkg.name}.conf"
if [ ! -f ${configfile} ]; then
  configfile=${pkg.installFolder}/conf/${pkg.name}.conf
fi

logbackfile="/config/logback.xml"
if [ ! -f ${logbackfile} ]; then
  logbackfile=${pkg.installFolder}/conf/logback.xml
fi

source "${CONF_FOLDER}/${configfile}"

echo "Starting ThingsBoard MQTT Broker Performance Tests..."

cd ${pkg.installFolder}/bin

if [ "$ORCHESTRATION_NODE" == "true" ]; then

  echo "Starting '${project.name}' in ORCHESTRATION mode..."

  java -cp ${jarfile} $JAVA_OPTS -Dloader.main=org.thingsboard.mqtt.broker.TestsOrchestratorApplication \
                    -Dlogging.config=${logbackfile} \
                    org.springframework.boot.loader.PropertiesLauncher

else
  echo "Starting '${project.name}' in WORKER mode..."

  java -cp ${jarfile} $JAVA_OPTS -Dloader.main=org.thingsboard.mqtt.broker.TestsApplication \
                    -Dlogging.config=${logbackfile} \
                    org.springframework.boot.loader.PropertiesLauncher

fi
