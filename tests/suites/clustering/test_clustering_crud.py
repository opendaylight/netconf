#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/901c7e139945b436d95a44b3b592904c3d7a4f9f/csit/suites/netconf/clustering/CRUD.robot
#

import textwrap

import allure
import pytest

from suites.clustering.clustering_crud_suite import ClusteringCrudSuite
from suites.suite_order import SuiteOrder


@pytest.mark.cluster
@pytest.mark.crud
@pytest.mark.testtool
@pytest.mark.functional
@pytest.mark.usefixtures("odl_three_node_cluster")
@pytest.mark.run(order=SuiteOrder.CLUSTERING_CRUD)
class TestClusteringCrud(ClusteringCrudSuite):

    DEVICE_TYPE = "configure-via-topology"
    CONFIGURE_BUG = "5089"
    DATA_OP_BUG = "4968"

    @allure.description(
        textwrap.dedent(
            """
            **Test suite to perform CRUD operations across an ODL cluster.**

            Perform basic operations (Create, Read, Update and Delete or CRUD) on \
            device data mounted onto a netconf connector and see if they work.

            The suite recognizes 3 nodes, "CONFIGURER" (the node that configures the \
            device at the beginning and then deconfigures it at the end), "SETTER" \
            (the node that manipulates the data on the device) and "CHECKER" (the node \
            that checks the data on the device). The configured device and the results \
            of each data operation on it is expected to be visible on all nodes so \
            after each operation three test cases make sure they can see the result on \
            their respective nodes.

            The suite checks the integrity of the presence of the device and the data \
            seen on the device only for nodes that have at least one of the roles \
            ("CONFIGURER", "SETTER" and "CHECKER") assigned. A better design would \
            have a "checker list" of sorts and have only one checking test case that \
            runs through the check list and performs the test on each node listed.
            """
        )
    )
    def test_crud_clustering(
        self, configured_device, allure_step_with_separate_logging
    ):
        self.run_crud_flow(allure_step_with_separate_logging)
