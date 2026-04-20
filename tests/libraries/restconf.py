#
# Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

import logging

from libraries.variables import variables

log = logging.getLogger(__name__)


RESTCONF_ROOT = variables.RESTCONF_ROOT


def generate_uri(
    identifier, datastore_flag: str = "config", node_value_list: list = ()
):
    """Returns the proper URI to use. Variable input error checking is done to ensure the ${datastore_flag} variable
    is config, operational or rpc. @{node_value_list} is expected to be in the format of node=value. RFC8040 can
    use that as is with '=' delimiter

    Args:
        identifier ():

    Returns:
        paramiko.SSHClient: Connected SSH client.
    """
    uri = generate_rfc8040_uri(identifier, datastore_flag, node_value_list)

    return uri


def generate_rfc8040_uri(
    identifier, datastore_flag: str = "config", node_value_list: list = ()
):
    node_value_path = ""
    for nv in node_value_list:
        node_value_path += f"/{nv}"
    if datastore_flag == "config":
        uri = f"{RESTCONF_ROOT}/data/{identifier}{node_value_path}?content=config"
    elif datastore_flag == "operational":
        uri = f"{RESTCONF_ROOT}/data/{identifier}{node_value_path}?content=nonconfig"
    else:
        uri = f"{RESTCONF_ROOT}/operations/{identifier}"

    return uri
