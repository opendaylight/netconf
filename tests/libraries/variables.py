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
from pydantic_settings import BaseSettings


class Variables(BaseSettings):
    """
    Defines all global test settings, which can be overridden by environment
    variables.
    """

    ODL_IP: str = "127.0.0.1"
    ODL_USER: str = "admin"
    ODL_PASSWORD: str = "admin"
    RESTCONF_PORT: int = 8181
    RESTCONF_ROOT: str = "rests"
    TOOLS_IP: str = "127.0.1.0"
    KARAF_LOG_LEVEL: str = "INFO"
    HEADERS: ClassVar[dict] = {"Content-Type": "application/json"}
    MAX_HTTP_RESPONSE_BODY_LOG_SIZE: int = 5000

    ODL_NETCONF_MDSAL_PORT: int = 2830
    ODL_NETCONF_PASSWORD: str = "admin"
    ODL_NETCONF_PROMPT: str = "]]>]]>"
    ODL_NETCONF_USER: str = "admin"

    CALLHOME_WHITELIST: str = "rests/data/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices"
    NETCONF_KEYSTORE_DATA_URL: str = "rests/data/netconf-keystore:keystore"


variables = Variables()
