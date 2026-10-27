#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

"""Shared helpers for AAA's authentication configuration (Shiro's
`main`/`urls` filter chain): reading, writing and restoring it over
RESTCONF, plus confirming that a change to it has actually taken effect
live, not just been written.

A suite uploads its whole configuration as one static file rather than
building it up from pieces here - merging onto an unknown prior state is
itself a source of bugs. It saves whatever was configured before first, so
that can be restored once the suite is done. There's also a narrower case:
toggling a single `main` setting in place without touching anything else.

Each write is sent as a whole, not as several smaller ones: doing several
writes in quick succession has been seen to occasionally leave the
underlying service stuck.
"""

import json
import logging
import re
import time

import requests

from libraries import infra
from libraries import jaxrs_restconf
from libraries import templated_requests
from libraries import utils
from libraries.variables import variables

log = logging.getLogger(__name__)

ODL_IP = variables.ODL_IP

_AAASHIRO_FILTER_NAME = "org.opendaylight.aaa.shiro.filters.AAAShiroFilter"
_RESTCONF_CONTEXT_SELECTOR = "osgi.http.whiteboard.context.name=RESTCONF"

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

SHIRO_CONFIG_URI = (
    f"{jaxrs_restconf.RESTCONF_ROOT}/data/aaa-app-config:shiro-configuration"
)
SHIRO_CONFIGURATION_KEY = "aaa-app-config:shiro-configuration"


def get_restconf_aaa_shiro_filter_id(host: str = ODL_IP) -> str | None:
    """Returns the id of the filter that currently handles RESTCONF access
    control, or None if it isn't active at all.

    This id changes every time the filter is reloaded, so comparing it
    before and after a configuration change is a reliable way to tell
    whether that change has actually taken effect live yet.

    Args:
        host (str): Node to check.

    Returns:
        str | None: Current filter service id, or None.

    Raises:
        AssertionError: The command itself failed (e.g. a dropped Karaf
            shell connection) - distinct from the filter genuinely not
            being registered, which returns None instead.
    """
    stdout, stderr = infra.execute_karaf_command(
        "service:list -a javax.servlet.Filter", host=host
    )
    assert not stderr, f"failed to query the RESTCONF AAAShiroFilter: {stderr}"
    for block in stdout.split("[javax.servlet.Filter]"):
        if _AAASHIRO_FILTER_NAME in block and _RESTCONF_CONTEXT_SELECTOR in block:
            match = re.search(r"service\.id = (\d+)", block)
            if match:
                return match.group(1)
    return None


def wait_until_restconf_filter_reloads(
    previous_id: str | None,
    host: str = ODL_IP,
    reload_timeout: int = 45,
    settle_duration: float = 1,
):
    """Waits until the id of the filter handling RESTCONF access control
    changes from `previous_id`, then confirms it stays the same for a short
    while - a reload can be immediately followed by another overlapping
    one, so seeing one new id isn't yet proof it's the final one.

    Args:
        previous_id (str | None): Filter id to consider stale.
        host (str): Node to check.
        reload_timeout (int): Seconds to wait for the first sign of a
            reload before giving up.
        settle_duration (float): Seconds the id must stay unchanged before
            it's considered final.
    """

    def _changed():
        current = get_restconf_aaa_shiro_filter_id(host=host)
        assert current is not None and current != previous_id, (
            f"the RESTCONF AAAShiroFilter is still {current!r}, hasn't "
            "been reloaded yet"
        )
        return current

    settled_id = utils.wait_until_function_pass(reload_timeout, 1, _changed)

    # Watch for settle_duration seconds of no further change. If it changes
    # again during that window, treat the new id as the one to watch and
    # restart the countdown, rather than keep comparing against an id
    # that's already outdated. None (filter momentarily unregistered
    # mid-reload) always restarts the countdown, so it can never be
    # accepted as the final id. An overall cap guards against it never
    # settling at all.
    overall_timeout = max(reload_timeout, 2 * settle_duration)
    overall_deadline = time.time() + overall_timeout

    settle_deadline = time.time() + settle_duration
    while time.time() < settle_deadline:
        assert time.time() < overall_deadline, (
            f"the RESTCONF AAAShiroFilter never settled - still changing "
            f"after {overall_timeout} seconds, last seen as {settled_id!r}"
        )
        time.sleep(settle_duration / 5)
        current = get_restconf_aaa_shiro_filter_id(host=host)
        if current is None or current != settled_id:
            settled_id = current
            settle_deadline = time.time() + settle_duration

    jaxrs_restconf.wait_for_ready(host=host)


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


def write_shiro_configuration(
    config: dict, host: str = ODL_IP, confirm_reload: bool = True
):
    """Overwrites the whole `shiro-configuration` container with exactly
    `config` - shaped the same way read_shiro_configuration returns it, so
    a value it returned can be written straight back here.

    Args:
        config (dict): Whole shiro-configuration container to write.
        host (str): Node to write to.
        confirm_reload (bool): Whether to confirm the RESTCONF
            AAAShiroFilter has actually reloaded afterwards, the same way
            set_parser_config does - the web server answering again isn't
            proof this specific data was picked up yet. Pass False for a
            caller that's about to restart Karaf anyway: a live reload
            isn't reliable proof a brand new realm/filter chain was picked
            up, so there's no point confirming one here first.
    """
    previous_filter_id = (
        get_restconf_aaa_shiro_filter_id(host=host) if confirm_reload else None
    )
    templated_requests.put_to_uri_request(
        SHIRO_CONFIG_URI,
        headers={"Content-Type": "application/yang-data+json"},
        data=json.dumps(config),
        host=host,
        base_url=jaxrs_restconf.base_url(host),
    )
    if confirm_reload:
        wait_until_restconf_filter_reloads(previous_filter_id, host=host)


def delete_shiro_configuration(host: str = ODL_IP, confirm_reload: bool = True):
    """Deletes the whole `shiro-configuration` container entirely.

    Args:
        host (str): Node to write to.
        confirm_reload (bool): See write_shiro_configuration.
    """
    previous_filter_id = (
        get_restconf_aaa_shiro_filter_id(host=host) if confirm_reload else None
    )
    templated_requests.delete_from_uri_request(
        SHIRO_CONFIG_URI,
        expected_code=(204, 409),
        host=host,
        base_url=jaxrs_restconf.base_url(host),
    )
    if confirm_reload:
        wait_until_restconf_filter_reloads(previous_filter_id, host=host)


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
