#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/901c7e139945b436d95a44b3b592904c3d7a4f9f/csit/suites/netconf/clustering/bug8086.robot
#

import textwrap

import allure
import pytest

from libraries import infra
from suites.clustering.clustering_crud_suite import ClusteringCrudSuite
from suites.suite_order import SuiteOrder

# Directory the device's netconf connector is told to use as its schema cache,
# pre-populated with the model below before the device is configured. This is
# the Bug 8086 setup: the connector is pointed at a schema cache holding a
# model the device also advertises.
SCHEMA_DIRECTORY = "/tmp/schema"
SCHEMA_MODEL = "car@2014-08-18.yang"
# The testtool serves this model too, so the copy the cache is seeded with is
# taken from the same place the testtool gets it, instead of downloading the
# clustering-it-model sources artifact the original Robot suite pulled from
# Nexus.
SCHEMA_SOURCE_DIRECTORY = "variables/netconf/CRUD/schemas"


@pytest.mark.cluster
@pytest.mark.crud
@pytest.mark.testtool
@pytest.mark.functional
@pytest.mark.usefixtures("odl_three_node_cluster")
@pytest.mark.run(order=SuiteOrder.CLUSTERING_BUG8086)
class TestClusteringBug8086(ClusteringCrudSuite):

    DEVICE_TYPE = "bug8086"
    SCHEMA_DIRECTORY = SCHEMA_DIRECTORY
    # The original suite attributes no step to a known bug.
    CONFIGURE_BUG = None
    DATA_OP_BUG = None

    @pytest.fixture()
    def device_schema_directory(self, allure_step_with_separate_logging):
        """Creates the schema cache directory and seeds it with the car model.

        The original Robot suite runs this over SSH on every cluster node,
        because there each node is a separate machine. Every member here shares
        one filesystem, so a single directory serves all three.

        Args:
            allure_step_with_separate_logging: Fixture used to log distinct steps
                into the Allure report.

        Yields:
            str: Path of the prepared schema cache directory.
        """
        with allure_step_with_separate_logging("step_populate_schema_directory"):
            # Create the schema directory and put the car model in it.
            infra.shell(
                f"mkdir -p {SCHEMA_DIRECTORY} && "
                f"cp {SCHEMA_SOURCE_DIRECTORY}/{SCHEMA_MODEL} {SCHEMA_DIRECTORY}/"
            )
        yield SCHEMA_DIRECTORY
        with allure_step_with_separate_logging("step_remove_schema_directory"):
            # Remove what the suite created so a re-run starts from scratch.
            infra.shell(f"rm -rf {SCHEMA_DIRECTORY}")

    @allure.description(
        textwrap.dedent(
            """
            **Simplified netconf clustered CRUD test suite in Bug 8086 setup.**

            Perform basic operations (Create, Read, Update and Delete or CRUD) on \
            device data mounted onto a netconf connector and see if they work, with \
            the connector configured to use a schema cache directory that has been \
            pre-populated with a model the device advertises.

            The suite recognizes 3 nodes, "CONFIGURER" (the node that configures the \
            device at the beginning and then deconfigures it at the end), "SETTER" \
            (the node that manipulates the data on the device) and "CHECKER" (the node \
            that checks the data on the device). The configured device and the results \
            of each data operation on it is expected to be visible on all nodes so \
            after each operation three test cases make sure they can see the result on \
            their respective nodes.
            """
        )
    )
    def test_bug8086(self, configured_device, allure_step_with_separate_logging):
        self.run_crud_flow(allure_step_with_separate_logging)
