#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/master/csit/suites/netconf/scale/max_devices.robot
#

import csv
import logging
import textwrap

import allure
import pytest

from libraries import infra
from libraries import netconf
from libraries import templated_requests
from libraries import TopologyNetconfNodes
from libraries.variables import variables
from suites.suite_order import SuiteOrder

INIT_DEVICE_COUNT = 20
MAX_DEVICE_COUNT = 3000
DEVICE_INCREMENT = 20
DEVICE_COUNTS = list(range(INIT_DEVICE_COUNT, MAX_DEVICE_COUNT + 1, DEVICE_INCREMENT))
DEVICE_NAME_BASE = "netconf-scaling-device"
BASE_PORT = netconf.FIRST_TESTTOOL_PORT
NUM_WORKERS = 20
TIMEOUT_FACTOR = 20
MIN_CONNECT_TIMEOUT = 300
DEVICES_RESULT_FILE = "results/devices.csv"
CRUD_SCHEMAS = "variables/netconf/CRUD/schemas"
SCHEMA_MODEL = "juniper"

ODL_IP = variables.ODL_IP
TOOLS_IP = variables.TOOLS_IP
RESTCONF_PORT = variables.RESTCONF_PORT
RESTCONF_ROOT = variables.RESTCONF_ROOT
ODL_NETCONF_NAMESPACE = variables.ODL_NETCONF_NAMESPACE

RESTCONF_URL = f"http://{ODL_IP}:{RESTCONF_PORT}/{RESTCONF_ROOT}"

log = logging.getLogger(__name__)


def _get_juniper_schemas() -> str:
    """Clone the Juniper YANG repository and extract schemas for testtool.

    Returns:
        str: Path to the directory containing the extracted Juniper YANG schemas.
    """
    juniper_schemas_dir = "tmp/junos_19.4R1"
    infra.shell("git clone --depth 1 https://github.com/Juniper/yang.git", cwd="tmp")
    infra.shell(f"mkdir -p {juniper_schemas_dir}")
    infra.shell(
        f"find tmp/yang/19.4/19.4R1/junos -type f -name '*.yang'"
        f" -exec cp {{}} {juniper_schemas_dir}/ \\;"
    )
    infra.shell(f"cp tmp/yang/19.4/19.4R1/common/* {juniper_schemas_dir}/")
    return juniper_schemas_dir


@pytest.mark.testtool
@pytest.mark.performance
@pytest.mark.multi_device
@pytest.mark.standalone
@pytest.mark.usefixtures("odl_standalone")
@pytest.mark.run(order=SuiteOrder.SCALE_MAX_DEVICES)
class TestMaxDevices:

    @allure.description(
        textwrap.dedent(
            """
            **Netconf scaling test to find the maximum number of connected devices.**

            Iterates over increasing device counts in DEVICE_INCREMENT steps from \
            INIT_DEVICE_COUNT up to MAX_DEVICE_COUNT. Each iteration restarts \
            testtool with the new total, registers only the delta of new devices \
            in ODL topology, awaits connection, and verifies GET responses. The \
            loop stops at the first failure. All registered devices and the \
            testtool process are removed in the teardown regardless of outcome. \
            The maximum successfully verified count is recorded to a CSV file.
            """
        )
    )
    def test_find_max_devices(self, allure_step_with_separate_logging):
        schemas = _get_juniper_schemas() if SCHEMA_MODEL == "juniper" else None
        maximum_devices = 0
        device_names = []
        testtool_process = None

        try:
            for device_count in DEVICE_COUNTS:
                timeout = max(device_count * TIMEOUT_FACTOR, MIN_CONNECT_TIMEOUT)

                with allure_step_with_separate_logging(
                    f"step_start_testtool_{device_count}_devices"
                ):
                    testtool_process = netconf.start_testtool(
                        "build_tools/netconf-testtool.jar",
                        device_count=device_count,
                        schemas=schemas,
                        debug=False,
                    )

                new_count = device_count - len(device_names)
                first_id = len(device_names)
                with allure_step_with_separate_logging(
                    f"step_configure_{new_count}_new_devices"
                ):
                    # Register only the delta of new devices; existing ones are kept.
                    new_names = TopologyNetconfNodes.configure_device_range(
                        restconf_url=RESTCONF_URL,
                        device_name_prefix=DEVICE_NAME_BASE,
                        device_ipaddress=TOOLS_IP,
                        device_port=BASE_PORT + first_id,
                        device_count=new_count,
                        use_node_encapsulation=True,
                        first_device_id=first_id,
                    )
                    device_names.extend(new_names)

                with allure_step_with_separate_logging(
                    f"step_await_{device_count}_devices_connected"
                ):
                    # Wait for all registered devices to reach connected state.
                    TopologyNetconfNodes.await_devices_connected(
                        restconf_url=RESTCONF_URL,
                        device_names=device_names,
                        deadline_seconds=timeout,
                        use_node_encapsulation=True,
                    )

                with allure_step_with_separate_logging(
                    f"step_verify_get_requests_on_{device_count}_devices"
                ):
                    # Issue parallel GET requests to all devices and verify responses.
                    netconf.get_data_from_devices_concurrently(
                        device_count, NUM_WORKERS
                    )

                with allure_step_with_separate_logging(
                    f"step_stop_testtool_{device_count}_devices"
                ):
                    if testtool_process is not None:
                        netconf.stop_testtool(testtool_process)

                maximum_devices = device_count

        except Exception as exc:
            log.warning("Scaling stopped at %d devices: %s", maximum_devices, exc)

        finally:
            if testtool_process is not None:
                with allure_step_with_separate_logging("step_stop_testtool"):
                    netconf.stop_testtool(testtool_process)

            if device_names:
                with allure_step_with_separate_logging("step_deconfigure_all_devices"):
                    mapping = {
                        "RESTCONF_ROOT": RESTCONF_ROOT,
                    }
                    templated_requests.delete_templated_request(
                        "variables/netconf/scale/remove_all_nodes", mapping=mapping
                    )

            with allure_step_with_separate_logging("step_record_results"):
                # Append the maximum successfully verified device count to results.
                with open(DEVICES_RESULT_FILE, "w", newline="") as f:
                    writer = csv.writer(f)
                    writer.writerow(["Max Devices"])
                    writer.writerow([maximum_devices])

        assert (
            maximum_devices > 0
        ), "No iteration completed successfully; zero devices were verified."
