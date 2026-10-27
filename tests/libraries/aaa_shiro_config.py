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


def _write_shiro_configuration(config: dict | None, host: str = ODL_IP):
    """Overwrites the whole `shiro-configuration` container with exactly
    `config`, or deletes it entirely if `config` is None. Only writes -
    doesn't get the change applied; see
    write_shiro_configuration_and_restart and
    write_shiro_configuration_and_wait_for_reload for that.

    Args:
        config (dict | None): Whole shiro-configuration container to write,
            shaped the same way read_shiro_configuration returns it, or
            None to delete it.
        host (str): Node to write to.
    """
    if config is None:
        templated_requests.delete_from_uri_request(
            SHIRO_CONFIG_URI,
            expected_code=(204, 409),
            host=host,
            base_url=jaxrs_restconf.base_url(host),
        )
    else:
        templated_requests.put_to_uri_request(
            SHIRO_CONFIG_URI,
            headers={"Content-Type": "application/yang-data+json"},
            data=json.dumps(config),
            host=host,
            base_url=jaxrs_restconf.base_url(host),
        )


def write_shiro_configuration_and_restart(config: dict | None, host: str = ODL_IP):
    """Overwrites the whole `shiro-configuration` container with `config`,
    or deletes it entirely if `config` is None, then restarts Karaf to
    apply it.

    Restarts rather than relying on a live reload: a live reload isn't
    reliable proof the data was actually picked up - a request right after
    one can still be rejected as if the write never happened, even though
    the reload itself genuinely completed. Restarting guarantees a fresh
    read of the already-committed data, with no such race possible. Use
    this for a brand new realm/filter chain.

    Args:
        config (dict | None): Whole shiro-configuration container to write,
            shaped as read_shiro_configuration returns it, or None to
            delete it.
        host (str): Node to write to.
    """
    _write_shiro_configuration(config, host=host)
    infra.restart_karaf(host=host)
    jaxrs_restconf.wait_for_ready(host=host, retry_count=120)


def write_shiro_configuration_and_wait_for_reload(
    config: dict | None, host: str = ODL_IP
):
    """Overwrites the whole `shiro-configuration` container with `config`,
    or deletes it entirely if `config` is None, then waits until the
    RESTCONF AAAShiroFilter has reloaded and settled - the web server
    answering again isn't proof this specific data was picked up yet.

    `config` must differ from what's already there: a write that changes
    nothing doesn't reload the filter, so the wait would time out.

    Args:
        config (dict | None): Whole shiro-configuration container to write,
            shaped as read_shiro_configuration returns it, or None to
            delete it.
        host (str): Node to write to.
    """
    previous_filter_id = get_restconf_aaa_shiro_filter_id(host=host)
    _write_shiro_configuration(config, host=host)
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
    return _get_main_pair_value(read_shiro_configuration(host=host), key)


def _get_main_pair_value(config: dict | None, key: str) -> str | None:
    """Looks up one `main` pair-key's value in already-read `config`.

    Args:
        config (dict | None): shiro-configuration as returned by
            read_shiro_configuration.
        key (str): `main` pair-key to look up.

    Returns:
        str | None: The pair-key's value, or None if not declared.
    """
    main = (config or {}).get(SHIRO_CONFIGURATION_KEY, {}).get("main", [])
    entry = next((entry for entry in main if entry["pair-key"] == key), None)
    return entry["pair-value"] if entry else None


def write_main_pair_value(key: str, value: str | None, host: str = ODL_IP):
    """Sets one `main` pair-key to `value` (declaring it if not already
    present), or removes it entirely if `value` is None, and writes the
    result back in a single combined write. Every other `main`/`urls`
    entry is left untouched - e.g. declaring `roles` this way overrides
    Shiro's built-in filter of that name for every `urls` entry that
    already references `roles[...]`, no `urls` change needed.

    Does nothing if `key` already has `value` (or is already absent when
    `value` is None): such a write wouldn't change anything, so AAA
    wouldn't reload and confirming the reload would time out.

    Args:
        key (str): `main` pair-key to set.
        value (str | None): Value to set it to, or None to remove the
            pair-key entirely.
        host (str): Node to write to.
    """
    config = read_shiro_configuration(host=host)
    if _get_main_pair_value(config, key) == value:
        log.info(f"`main` pair-key {key!r} is already {value!r}, nothing to write")
        return
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
    write_shiro_configuration_and_wait_for_reload(config, host=host)
