# CSIT - Continuous System Integration Test

## Overview

This directory contains **Continuous System Integration Tests (CSIT)** and associated testing tools
for the **NETCONF** project. Test are written using common python testing framework, PyTest. These tests are used to test
the core **NETCONF** and **RESTCONF** functionality of Opendaylight distribution. These tests are run directly against
built opendaylight distribution with its associated test tools without relying on any external repository with prebuilt artifacts
like nexus.

## Test File Origin and History

Test data files and test tools scripts contained in this repository were originally created as part of separate
testing project which used Robot Framework library for system testing. Now as part of moving to more
lightweight and simpler PyTest framework part of the original test files were reused.

## Directory Structure

The main directory structure is as following:

| Directory              | Description                                                                                                            |
| :--------------------- | :----------------------------------------------------------------------------------------------------------------------|
| `allure-results`       | Directory used for storing Allure reports generated during test runs.                                                  |
| `build_tools`          | Directory containing built test tool jars (e.g. `netconf-testtool.jar`, `rest-perf-client.jar`) used to run tests.     |
| `libraries`            | Directory containing reusable test functions used in test cases.                                                      |
| `opendaylight-member-*`| Directories containing the extracted Opendaylight distribution(s) started as cluster members during test execution.   |
| `results`              | Directory used for storing test results (e.g., logs, performance metrics).                                            |
| `suites`               | Directory containing test suites.                                                                                      |
| `tmp`                  | Directory containing temporary files used during test execution, but are cleared at the start of the test run.        |
| `tools`                | Directory containing custom test tools, such as `start_netopeer.sh` for starting the netopeer2 device simulator (with call-home and key-auth support) or websocket/SSE receivers used to verify notification streams. |
| `variables`            | Directory containing test data (mostly templates used for NETCONF/RESTCONF calls).                                    |

## Netopeer2 Simulator

The suites marked with the `netopeer2` pytest marker (`callhome` and `KeyAuth`) exercise the
NETCONF Call Home and Key Auth features against a real NETCONF device, simulated with a
[sysrepo/sysrepo-netopeer2](https://hub.docker.com/r/sysrepo/sysrepo-netopeer2) Docker container.
This container is **not** started automatically by the test run, so it must be started once
beforehand and left running for the duration of the test session:

```sh
tools/start_netopeer.sh
```

The script builds a scratch directory under `/tmp/pytest-netopeer/`, generates the SSH host/client
keys and TLS certificates the container needs, stages the Key Auth public key, and brings the
container up in the background via `docker compose`. Requirements: `docker`, `ssh-keygen` and
`openssl` available on `PATH`. Once the container is up, the `callhome` and `KeyAuth` suites can be
run as usual; the container can be left running across multiple test invocations and only needs to
be re-run if the generated keys/certs need to be regenerated.

## Test Execution

Tests can be executed using two primary methods:

### 1. Part of maven build

Test can be run directly during maven build using `integration-tests` profile

```sh
mvn clean install -Pintegration-tests
```

### 2. Using tox command

Tests can be also run separately using tox tool which needs to be invoked from tests directory by running commands such as:

```sh
cd tests
tox -e pytest
```

### Test results

Test results are evaluated using allure reporting system. To view the generated test report run the following command:

```sh
allure serve tests/allure-results/
```
