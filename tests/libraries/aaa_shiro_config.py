#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

"""Shared helpers for editing AAA's Apache-Shiro `main`/`urls` filter-chain
configuration (module `aaa-app-config`, container `shiro-configuration`)
over RESTCONF - used by any suite wiring a custom Shiro realm/filter into
the aaa-shiro blueprint chain (see oauth_proxy_header.py). A suite uploads
its whole `main`/`urls` payload as one static file rather than building it
up here from pieces - merging onto an unknown prior state is itself a
source of bugs - after saving whatever was configured before via
read_shiro_configuration, so restore_shiro_configuration can put it back
once the suite is done. This module's read/write/restore primitives are
for that save/restore, plus the narrower case of toggling a single `main`
key in place (see read_main_pair_value/write_main_pair_value).

Important limitation: this configuration is only ever consulted by
aaa-shiro's classic Jetty-based `AAAShiroFilter`, reachable only through the
JAX-RS RESTCONF implementation (see jaxrs_restconf.py) - not the project's
usual Netty-based RESTCONF northbound, whose own auth handler
(`AAAShiroPrincipalService`) only ever builds a Basic-Auth
`UsernamePasswordToken` regardless of what's written here (confirmed by
reading its source). Both implementations read/write the same MD-SAL data,
so using this module only ever changes which HTTP implementation serves a
request, never what data exists.

Every `main`/`urls` change goes through a single whole-container read +
write (read_shiro_configuration/write_shiro_configuration), never smaller
per-key writes: a keyless list node isn't a valid RESTCONF PUT target
(confirmed by hand), and each write restarts the whole aaa-shiro blueprint
- several separate writes for one conceptual change has been confirmed by
hand to occasionally leave the bundle permanently stuck (`Active/Waiting`,
via an unrelated, pre-existing AAA IDM bean-construction defect this module
has no control over).
"""

import json
import logging

import requests

from libraries import jaxrs_restconf
from libraries import templated_requests
from libraries.variables import variables

log = logging.getLogger(__name__)

ODL_IP = variables.ODL_IP

# Shiro's built-in `roles` filter alias (org.apache.shiro.web.filter.authz.
# RolesAuthorizationFilter) returns 401 Unauthorized, not 403 Forbidden, when
# an authenticated user lacks the required role - confirmed by decompiling
# it: AuthorizationFilter.onAccessDenied only ever redirects to an
# unauthorizedUrl (not configured here) or sends 401, there is no 403 code
# path in it at all. ODLRolesAuthorizationFilter is aaa-shiro's own opt-in
# replacement that overrides onAccessDenied to send 403 instead once a
# subject is known to be authenticated - toggle it on via
# write_main_pair_value(ROLES_KEY, ODL_ROLES_AUTHORIZATION_FILTER_CLASS).
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
    """Reads the whole shiro-configuration container, exactly as RESTCONF
    represents it (`{SHIRO_CONFIGURATION_KEY: {"main": [...], "urls":
    [...]}}`) so the result can be passed straight back into
    write_shiro_configuration unchanged - or None if it isn't configured yet
    (RESTCONF data-missing), distinct from an empty-but-present container,
    which a write can leave behind but a genuinely unconfigured container
    never is. See restore_shiro_configuration for why that distinction
    matters.

    The aaa-app-config:shiro-configuration container isn't actually
    bootstrapped into the datastore from the shipped default XML in this
    environment (only copied under etc/, never imported) - yet admin/admin
    auth already works via AAA's hardcoded in-code default realm chain
    (tokenAuthRealm, backed by the H2/IDMLight store, per the shipped
    aaa-app-config.xml's own `$tokenAuthRealm` default).

    Args:
        host (str): Node to read from.

    Returns:
        dict | None: The whole shiro-configuration container, exactly as
            RESTCONF represents it, or None if not configured yet.
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
    a single write, and therefore a single aaa-shiro blueprint restart. See
    this module's own docstring for why collapsing a whole conceptual
    change into one write like this matters here.

    A container-level PUT like this is also the only way to write a `urls`
    entry at all, not just the preferred one: every `urls` pair-key is a
    Shiro ant-style URI pattern, so it always contains at least one literal
    `/`, which RFC 8040 requires percent-encoding as `%2F` in a single-entry
    PUT's URI segment - and Jetty 9.4's default UriCompliance mode rejects
    any request path containing `%2F` outright as "ambiguous", with a bare
    400 and no response body (confirmed by hand). Writing the whole
    container instead means the key only ever appears inside the JSON body,
    never the URL path, so it never hits that check.

    Args:
        config (dict): Whole shiro-configuration container to write, shaped
            the same way read_shiro_configuration returns it.
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


def restore_shiro_configuration(previous: dict | None, host: str = ODL_IP):
    """Restores the shiro-configuration container to exactly what an
    earlier read_shiro_configuration call captured - deleting the whole
    container if it was unconfigured back then, or writing the exact same
    `main`/`urls` back otherwise. Intended for a caller that uploads its own
    temporary configuration and wants the original put back afterwards (see
    oauth_proxy_header.py for an example).

    A RESTCONF PUT can only ever leave the container present, even with
    empty `main`/`urls` lists, never genuinely absent again. The shipped
    aaa-app-config.xml's own static defaults (tokenAuthRealm, the
    authcBasic catch-all, ...) only apply as a fallback while the container
    is truly absent, so writing it back as empty instead of deleting it
    breaks even admin/admin authentication entirely (confirmed by hand).

    Args:
        previous (dict | None): Prior state, as returned by an earlier
            read_shiro_configuration call.
        host (str): Node to write to.
    """
    if previous is None:
        templated_requests.delete_from_uri_request(
            SHIRO_CONFIG_URI,
            expected_code=(204, 409),
            host=host,
            base_url=jaxrs_restconf.base_url(host),
        )
        jaxrs_restconf.wait_for_ready(host=host)
    else:
        write_shiro_configuration(previous, host=host)


def read_main_pair_value(key: str, host: str = ODL_IP) -> str | None:
    """Reads one `main` pair-key's current value, or None if not declared
    (including when shiro-configuration isn't configured at all yet).

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
