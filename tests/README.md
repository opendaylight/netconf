# CSIT - Continuous System Integration Test

## Overview

This directory contains **Continuous System Integration Tests (CSIT)** and the test tools they use
for the **NETCONF** project. The tests are written using PyTest and cover the core **NETCONF** and
**RESTCONF** functionality of the OpenDaylight distribution. They run directly against a
distribution built from this repository, together with its test tools, and do not depend on any
external repository of prebuilt artifacts such as Nexus.

## Test File Origin and History

The test data files and test tool scripts contained in this directory were originally created as
part of a separate testing project which used the Robot Framework library for system testing. As
part of moving to the more lightweight and simpler PyTest framework, part of the original test
files were reused.

## Directory Structure

| Directory   | Description                                                                                                             |
| :---------- | :---------------------------------------------------------------------------------------------------------------------- |
| `libraries` | Reusable test functions used by the test cases.                                                                         |
| `suites`    | The test suites, one directory per suite.                                                                               |
| `tools`     | Custom test tools, such as `start_netopeer.sh` and the websocket and SSE receivers used to verify notification streams. |
| `variables` | Test data, mostly templates used for the NETCONF and RESTCONF calls.                                                    |

The following directories are produced by a test run and are not tracked:

| Directory               | Description                                                                             |
| :---------------------- | :-------------------------------------------------------------------------------------- |
| `allure-results`        | Raw results the Allure report is generated from.                                        |
| `build_tools`           | Test tool jars (`netconf-testtool.jar`, `rest-perf-client.jar`) copied in by the build. |
| `opendaylight`          | The OpenDaylight distribution started for the single node tests.                        |
| `opendaylight-member-*` | One distribution per cluster member, started for the clustering tests.                  |
| `results`               | Test tool logs and performance metrics.                                                 |
| `tmp`                   | Temporary files, cleared at the start of every run.                                     |

## Netopeer2 Simulator

The suites marked with the `netopeer2` PyTest marker, `callhome` and `KeyAuth`, exercise NETCONF
Call Home and key based authentication against a real NETCONF device, simulated by a
[sysrepo/sysrepo-netopeer2](https://hub.docker.com/r/sysrepo/sysrepo-netopeer2) container. Nothing
in a test run starts it, so it has to be started once beforehand and left running:

```sh
tests/tools/start_netopeer.sh
```

The script provisions `/tmp/pytest-netopeer/`, generates the SSH host keys and TLS certificates the
container needs, stages the key the KeyAuth suite authenticates with, labels the bind mounts for
SELinux and brings the container up in the background. It needs `docker`, `ssh-keygen` and
`openssl` on `PATH`. The same container then serves any number of runs. Re-run the script after a
reboot, as `/tmp` is a tmpfs on most distributions and the container loses everything it mounts.

## Test Execution

Tests can be executed using two primary methods:

### 1. Part of maven build

```sh
mvn clean install -Pintegration-tests
```

The `integration-tests` profile unpacks a fresh distribution into `tests/opendaylight`, copies the
test tool jars into `tests/build_tools` and runs PyTest during the `integration-test` phase. By
default it selects only the suites marked `smoke`, which is what the GitHub Actions verify job
runs.

Stopping after the `integration-test` phase and skipping the static analysis is quicker for local
work:

```sh
mvn clean integration-test -Pq,integration-tests
```

Individual suites can be selected by overriding the arguments passed on to PyTest:

```sh
mvn clean integration-test -Pq,integration-tests -Dtox.args='-- suites/CRUD'
```

### 2. Using tox command

Tests can also be run separately using the tox tool, which needs to be invoked from the tests
directory:

```sh
cd tests
tox -e pytest
```

Note that tox runs PyTest against whatever distribution is already in `tests/opendaylight` instead
of unpacking a fresh one as the maven build does. Started this way over a distribution left behind
by an earlier run, a suite finds the previous run's `System ready` line still in
`data/log/karaf.log` and carries on before the new instance is listening. Use the maven build
unless the distribution is known to be fresh.

### Test results

Test results are evaluated using the allure reporting system. To view the generated test report run
the following command:

```sh
allure serve tests/allure-results/
```
