import functools
import logging
import textwrap

import allure
import pytest
import requests

from suites.suite_order import SuiteOrder


log = logging.getLogger(__name__)


@pytest.mark.usefixtures("odl_three_node_cluster")
@pytest.mark.usefixtures("log_test_suite_start_end_to_karaf")
@pytest.mark.usefixtures("log_test_case_start_end_to_karaf")
@pytest.mark.run(order=SuiteOrder.GETMULTI)
class TestClustering:

    def test_clustering(self):

        payload = {
            "network-topology:node": [
                {
                "node-id": "clustering-device",
                "netconf-node-topology:netconf-node": {
                    "host": "127.0.0.100",
                    "port": 17830,
                    "login-password-unencrypted": {
                    "username": "admin",
                    "password": "admin"
                    },
                    "tcp-only": False,
                    "keepalive-delay": 0
                }
                }
            ]
        }
        
        response = requests.put(
            "http://127.0.0.1:8182/restconf/data/network-topology:network-topology/topology=topology-netconf/node=clustering-device", json=payload, auth=requests.auth.HTTPBasicAuth('admin', 'admin'), timeout=5
        )
        log.warning(response.text)

        for odl_ip in ("127.0.0.1", "127.0.0.2", "127.0.0.3"):
            url = f"http://{odl_ip}:8182/restconf/data/network-topology:network-topology/topology=topology-netconf"
            response = requests.get(
                url,
                auth=requests.auth.HTTPBasicAuth('admin', 'admin'),
                timeout=10,
            )

            log.warning(response.json())