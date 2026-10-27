#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

"""Library for testing AAA's OAuth2 Proxy Header authentication.

This uses aaa-shiro's `Oauth2ProxyHeaderFilter` / `Oauth2ProxyHeaderRealm`,
which trust a reverse proxy's `X-Forwarded-User` and `X-Forwarded-Groups`
headers instead of performing any OAuth2/OIDC handshake or JWT validation
themselves. Two things can be configured to change how they behave:

* the filter/realm chain is configuration data, edited over RESTCONF via
  aaa_shiro_config;
* the parser's numeric/regex limits (max-header-length, max-role-length,
  max-user-length, max-roles-per-user, allowed-chars) are a separate
  ConfigAdmin PID, edited via the Karaf shell rather than by rewriting its
  backing file directly - that approach was found to occasionally miss a
  rewrite outright and silently not apply it.
"""

import logging

import requests

from libraries import aaa_shiro_config
from libraries import infra
from libraries import jaxrs_restconf
from libraries import templated_requests
from libraries.variables import variables

ODL_IP = variables.ODL_IP

PARSER_CONFIG_PID = "org.opendaylight.aaa.shiro.oauth2proxyheaderparser"
PARSER_CONFIG_DEFAULTS = {
    "max-header-length": "4096",
    "max-role-length": "128",
    "max-user-length": "128",
    "max-roles-per-user": "200",
    # The hyphen is placed last in the character class deliberately: a `-`
    # anywhere else would need escaping to avoid being read as a *range*
    # (e.g. an unescaped `-` between `:` and `@` reads as the range ':'
    # through '@', dropping the literal hyphen entirely and breaking every
    # role/username containing one, like "netconf-admin-role"). Placed
    # last, it's always literal, with no escaping - and therefore no
    # dependency on how many layers of escaping this value passes through
    # on its way to the live regex.
    "allowed-chars": "[a-zA-Z0-9_.:@-]",
}

USER_HEADER = "X-Forwarded-User"
GROUPS_HEADER = "X-Forwarded-Groups"

log = logging.getLogger(__name__)


def _write_parser_config(config: dict, host: str = ODL_IP):
    """Writes `config` as the parser's settings - via the Karaf shell
    rather than by rewriting the backing file directly.

    Uses a single config:edit session, so every property lands in one
    config:update and the filter reloads once, not once per property as
    with config:property-set -p. --force discards any edit session a
    previous failed call may have left open on the shared shell. Every
    command is verified, so a lost edit session (e.g. after the shell
    reconnected mid-sequence) fails right away rather than surfacing
    later as a misleading "filter hasn't been reloaded" timeout.

    Args:
        config (dict): Properties to write, every one of them.
        host (str): Node to write to.
    """
    infra.execute_karaf_command(
        f"config:edit --force {PARSER_CONFIG_PID}", host=host, verify=True
    )
    for key, value in config.items():
        infra.execute_karaf_command(
            f'config:property-set {key} "{value}"', host=host, verify=True
        )
    infra.execute_karaf_command("config:update", host=host, verify=True)


def _read_parser_config(host: str = ODL_IP) -> dict:
    """Reads the oauth2proxyheaderparser ConfigAdmin PID's current
    properties via the Karaf shell.

    Args:
        host (str): Node to read from.

    Returns:
        dict: Property name to value, empty if the PID isn't configured
            yet.
    """
    stdout, _ = infra.execute_karaf_command(
        f"config:property-list --pid {PARSER_CONFIG_PID}", host=host, verify=True
    )
    # One "key = value" line per property.
    return dict(
        (part.strip() for part in line.split(" = ", 1))
        for line in stdout.splitlines()
        if " = " in line
    )


def set_parser_config(overrides: dict, host: str = ODL_IP):
    """Rewrites the oauth2proxyheaderparser ConfigAdmin PID using
    `overrides` - not a patch of the current file, a full rewrite, so any
    property not named in `overrides` is set to its default instead,
    regardless of whatever was there before.

    Does nothing if every property already has the requested value: such
    a write wouldn't change anything, so the filter wouldn't reload and
    confirming the reload would time out.

    Args:
        overrides (dict): Properties to set; any property not named here
            is set to its default instead.
        host (str): Node to write to.
    """
    config = PARSER_CONFIG_DEFAULTS | overrides
    current = _read_parser_config(host=host)
    if all(current.get(key) == value for key, value in config.items()):
        log.info(f"{PARSER_CONFIG_PID} already has {config}, nothing to write")
        return
    previous_filter_id = aaa_shiro_config.get_restconf_aaa_shiro_filter_id(host=host)
    _write_parser_config(config, host=host)
    aaa_shiro_config.wait_until_restconf_filter_reloads(previous_filter_id, host=host)


def reset_parser_config(host: str = ODL_IP):
    """Rewrites the oauth2proxyheaderparser ConfigAdmin PID to exactly the
    preset list of default values kept in this module.

    Args:
        host (str): Node to write to.
    """
    set_parser_config({}, host=host)


def get_with_proxy_headers(
    uri: str,
    username: str | None = None,
    groups: str | None = None,
    expected_code: int | None = None,
    host: str = ODL_IP,
) -> requests.Response:
    """Sends a GET request carrying the given proxy-injected headers.

    Deliberately does not send Authorization/Basic-Auth credentials
    (auth=None): the protected URI is guarded by these proxy headers, not
    by Basic Authentication, so ODL is expected to trust them as if a
    reverse proxy had already authenticated the caller.

    Sent to the JAX-RS RESTCONF endpoint, not the Netty one targeted by
    default: only the JAX-RS endpoint actually installs AAAShiroFilter, so
    it's the only one this filter/realm configuration has any effect on.

    Args:
        uri (str): URI to GET (without host/port).
        username (str | None): Value to send as `X-Forwarded-User`, the
            header is omitted entirely if None.
        groups (str | None): Value to send as `X-Forwarded-Groups`, the
            header is omitted entirely if None.
        expected_code (int | None): Asserted against the response status
            code if provided.
        host (str): Node to send the request to.

    Returns:
        requests.Response: Response returned by ODL.
    """
    headers = {}
    if username is not None:
        headers[USER_HEADER] = username
    if groups is not None:
        headers[GROUPS_HEADER] = groups

    return templated_requests.get_from_uri(
        uri,
        headers=headers,
        expected_code=expected_code,
        host=host,
        base_url=jaxrs_restconf.base_url(host),
        auth=None,
    )
