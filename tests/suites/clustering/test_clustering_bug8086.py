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

from libraries import cluster
from libraries import infra
from suites.clustering.clustering_crud_suite import ClusteringCrudSuite
from suites.clustering.clustering_crud_suite import DEVICE_SCHEMAS_DIRECTORY
from suites.suite_order import SuiteOrder

# Schema cache directory the device's netconf connector is configured with,
# seeded before the device is configured. This is the Bug 8086 setup: the
# connector is pointed at a cache that already holds models the device also
# advertises, so they are read from disk instead of fetched from the device.
#
# The name is relative, and deliberately not the "schema" default: ODL resolves
# it under each member's own cache directory, so every member gets a private
# copy. An absolute path would resolve to itself and have all three members
# share one directory, where their unsynchronised writes of the same file could
# leave a member reading a half-written model.
SCHEMA_CACHE_DIRECTORY = "bug8086-schema"
# car-people imports car and people, so the three are seeded together to keep
# the whole set off the wire.
SCHEMA_CACHE_MODELS = (
    "car@2014-08-18.yang",
    "car-people@2014-08-18.yang",
    "people@2014-08-18.yang",
)


@pytest.mark.cluster
@pytest.mark.crud
@pytest.mark.testtool
@pytest.mark.functional
@pytest.mark.usefixtures("odl_three_node_cluster")
@pytest.mark.run(order=SuiteOrder.CLUSTERING_BUG8086)
class TestClusteringBug8086(ClusteringCrudSuite):

    DEVICE_TYPE: str = "bug8086"
    # The original suite attributes no step to a known bug.
    CONFIGURE_BUG: str | None = None
    DATA_OP_BUG: str | None = None

    @pytest.fixture()
    def device_schema_directory(self, allure_step_with_separate_logging):
        """Seeds every member's schema cache directory with the models to load.

        Args:
            allure_step_with_separate_logging: Fixture used to log distinct steps
                into the Allure report.

        Yields:
            str: Name of the prepared schema cache directory.
        """
        # The original Robot suite unpacks the models from a Nexus artifact over
        # SSH on every cluster node, because there each node is a separate
        # machine. Here the members share a filesystem, but each resolves the
        # cache directory under its own distribution, so each is seeded from the
        # copies the testtool already serves. Nothing is cleaned up afterwards:
        # the directories live inside the member distributions, which staging
        # removes and recreates on every run.
        with allure_step_with_separate_logging("step_populate_schema_directories"):
            sources = " ".join(
                f"{DEVICE_SCHEMAS_DIRECTORY}/{model}" for model in SCHEMA_CACHE_MODELS
            )
            for member_dir in cluster.get_cluster_dirs():
                target = f"{member_dir}/cache/{SCHEMA_CACHE_DIRECTORY}"
                # infra.shell reports a failed command through its return code
                # instead of raising, and an unseeded cache would not fail the
                # suite: the models would simply be fetched from the device
                # again, leaving this a second copy of the CRUD suite.
                return_code, _ = infra.shell(
                    f"mkdir -p {target} && cp {sources} {target}/"
                )
                assert return_code == 0, f"failed to seed {target}"
                _, listing = infra.shell(f"ls {target}")
                missing = [m for m in SCHEMA_CACHE_MODELS if m not in listing]
                assert not missing, f"{target} is missing {missing}"
        yield SCHEMA_CACHE_DIRECTORY

    @allure.description(
        textwrap.dedent(
            """
            **Simplified netconf clustered CRUD test suite in Bug 8086 setup.**

            Perform basic operations (Create, Read, Update and Delete or CRUD) on \
            device data mounted onto a netconf connector and see if they work, with \
            the connector configured to use a schema cache directory that has been \
            pre-populated with models the device advertises.

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
