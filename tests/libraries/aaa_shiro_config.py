#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

"""Shared helpers for reading, writing and restoring AAA's authentication
configuration (Shiro's `main`/`urls` filter chain) over RESTCONF.

A suite uploads its whole configuration as one static file rather than
building it up here from pieces - merging onto an unknown prior state is
itself a source of bugs. It saves whatever was configured before first,
so that can be restored once the suite is done. This module's functions
cover that read/write/restore, plus a narrower case: toggling a single
`main` setting in place without touching anything else.

Every change here is written as a whole, in one go, rather than as several
smaller writes: each write restarts the underlying service, and doing that
repeatedly in quick succession has been seen to occasionally leave it
stuck.
"""

import json
import logging

import requests

from libraries import jaxrs_restconf
from libraries import templated_requests
from libraries.variables import variables

log = logging.getLogger(__name__)

ODL_IP = variables.ODL_IP

# Shiro's built-in `roles` filter returns 401 Unauthorized, not 403
# Forbidden, when an authenticated user lacks the required role.
# ODLRolesAuthorizationFilter is aaa-shiro's own opt-in replacement that
# sends 403 instead once a subject is known to be authenticated - toggle
# it on via write_main_pair_value(ROLES_KEY,
# ODL_ROLES_AUTHORIZATION_FILTER_CLASS).
ROLES_KEY = "roles"
ODL_ROLES_AUTHORIZATION_FILTER_CLASS = (
    "org.opendaylight.aaa.shiro.filters.ODLRolesAuthorizationFilter"
)

SHIRO_CONFIG_URI = f"{jaxrs_restconf.RESTCONF_ROOT}/data/aaa-app-config:shiro-configuration"
SHIRO_CONFIGURATION_KEY = "aaa-app-config:shiro-configuration"


def is_data_missing(response: requests.Response) -> bool:
    """True for RFC 8040's "data-missing" error on a GET of something that
    doesn't exist yet. Per RFC 8040 Appendix A.1 this maps to HTTP 409
    (Conflict), not 404 - so status code alone can't tell "not configured
    yet" apart from a real conflict; the error-tag in the body can.

    Args:
        response (requests.Response): Response to a GET of
            aaa-app-config:shiro-configuration.

    Returns:
        bool: True if `response` represents RFC 8040's data-missing error.
    """
    if response.status_code != 409:
        return False
    try:
        errors = response.json()["errors"]["error"]
    except (ValueError, KeyError, IndexError):
        return False
    return any(error.get("error-tag") == "data-missing" for error in errors)


def read_shiro_configuration(host: str = ODL_IP) -> dict | None:
    """Reads the whole shiro-configuration data, exactly as RESTCONF
    represents it. Returns the config if present, or None if it isn't
    configured yet (RESTCONF data-missing) - distinct from an
    empty-but-present container, which a write can leave behind but a
    genuinely unconfigured container never is.

    The result can be written back directly, or used as a starting point
    to apply further changes before writing.

    Args:
        host (str): Node to read from.

    Returns:
        dict | None: The whole shiro-configuration data, or None if not
            configured yet.
    """
    response = templated_requests.get_from_uri(
        SHIRO_CONFIG_URI,
        headers={"Accept": "application/yang-data+json"},
        expected_code=(200, 409),
        host=host,
        base_url=jaxrs_restconf.base_url(host),
    )
    if is_data_missing(response):
        return None
    return response.json()


def write_shiro_configuration(config: dict, host: str = ODL_IP):
    """Overwrites the whole `shiro-configuration` container with exactly
    `config` - shaped the same way read_shiro_configuration returns it - in
    a single write, and therefore a single aaa-shiro blueprint restart.

    Args:
        config (dict): Whole shiro-configuration container to write.
        host (str): Node to write to.
    """
    templated_requests.put_to_uri_request(
        SHIRO_CONFIG_URI,
        headers={"Content-Type": "application/yang-data+json"},
        data=json.dumps(config),
        host=host,
        base_url=jaxrs_restconf.base_url(host),
    )
    jaxrs_restconf.wait_for_ready(host=host)


def restore_shiro_configuration(previous_config: dict | None, host: str = ODL_IP):
    """Overwrites the shiro-configuration container with `previous_config`,
    or deletes it entirely if `previous_config` is None. Intended for a
    caller that uploads its own temporary configuration and wants the
    original put back afterwards.

    Args:
        previous_config (dict | None): Configuration to restore, or None
            to delete the container entirely.
        host (str): Node to write to.
    """
    if previous_config is None:
        templated_requests.delete_from_uri_request(
            SHIRO_CONFIG_URI,
            expected_code=(204, 409),
            host=host,
            base_url=jaxrs_restconf.base_url(host),
        )
        jaxrs_restconf.wait_for_ready(host=host)
    else:
        write_shiro_configuration(previous_config, host=host)


def read_main_pair_value(key: str, host: str = ODL_IP) -> str | None:
    """Reads one `main` pair-key's current value, or returns None if not
    declared (including when shiro-configuration isn't configured at all
    yet).

    Args:
        key (str): `main` pair-key to look up.
        host (str): Node to read from.

    Returns:
        str | None: The pair-key's current value, or None if not declared.
    """
    config = read_shiro_configuration(host=host)
    if not config:
        return None
    main = config.get(SHIRO_CONFIGURATION_KEY, {}).get("main", [])
    if not main:
        return None
    entry = next((entry for entry in main if entry["pair-key"] == key), None)
    if not entry:
        return None
    return entry["pair-value"]


def write_main_pair_value(key: str, value: str | None, host: str = ODL_IP):
    """Sets one `main` pair-key to `value` (declaring it if not already
    present), or removes it entirely if `value` is None, and writes the
    result back in a single combined write. Every other `main`/`urls`
    entry is left untouched - e.g. declaring `roles` this way overrides
    Shiro's built-in filter of that name for every `urls` entry that
    already references `roles[...]`, no `urls` change needed.

    Args:
        key (str): `main` pair-key to set.
        value (str | None): Value to set it to, or None to remove the
            pair-key entirely.
        host (str): Node to write to.
    """
    config = read_shiro_configuration(host=host)
    if config is None:
        config = {SHIRO_CONFIGURATION_KEY: {}}
    main = config.setdefault(SHIRO_CONFIGURATION_KEY, {}).get("main", [])
    if value is None:
        main = [entry for entry in main if entry["pair-key"] != key]
    else:
        entry = next((entry for entry in main if entry["pair-key"] == key), None)
        if entry is None:
            main.append({"pair-key": key, "pair-value": value})
        else:
            entry["pair-value"] = value
    config[SHIRO_CONFIGURATION_KEY]["main"] = main
    write_shiro_configuration(config, host=host)
