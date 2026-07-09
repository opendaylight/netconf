#
# Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# These variables are considered global and immutable, so their names are in ALL_CAPS.
#

from typing import ClassVar

from controller_testlib.variables import ControllerVariables


class Variables(ControllerVariables):
    """
    Defines all global test settings, which can be overridden by environment
    variables.
    """

    RESTCONF_PORT: int = 8182
    RESTCONF_ROOT: str = "restconf"
    ODL_FEATURES: ClassVar[list[str]] = [
        "odl-infrautils-ready",
        "odl-restconf-nb",
        "odl-netconf-mdsal",
        "odl-restconf-openapi",
        "odl-clustering-test-app",
        "odl-netconf-topology",
        "odl-netconf-callhome-ssh",
    ]
    HEADERS: ClassVar[dict] = {"Content-Type": "application/json"}
    HEADERS_YANG_RFC8040_JSON: ClassVar[dict] = {
        "Content-Type": "application/yang-data+json"
    }
    MAX_HTTP_RESPONSE_BODY_LOG_SIZE: int = 10000
    MAX_VISUAL_DIFF_LOG_SIZE: int = 10000
    ENABLE_GLOBAL_TEST_DEADLINES: bool = True

    ODL_NETCONF_MDSAL_PORT: int = 2830
    ODL_NETCONF_PASSWORD: str = "admin"
    ODL_NETCONF_PROMPT: str = "]]>]]>"
    ODL_NETCONF_USER: str = "admin"
    ODL_NETCONF_NAMESPACE: str = "urn:ietf:params:xml:ns:netconf:base:1.0"

    CALLHOME_WHITELIST: str = (
        "restconf/data/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices"
    )
    NETCONF_KEYSTORE_DATA_URL: str = "restconf/data/netconf-keystore:keystore"

    MODULES_API: str = (
        "/restconf/data/ietf-yang-library:modules-state?content=nonconfig"
    )

    USE_NETCONF_CONNECTOR: bool = False

    XML_MESSAGE_HEADERS: ClassVar[dict] = {
        "Content-Type": "application/xml",
        "Accept": "application/xml",
    }


variables = Variables()
