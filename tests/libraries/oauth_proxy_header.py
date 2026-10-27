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
themselves. Wiring is entirely data-driven:

* the filter/realm chain is YANG-modeled data (module `aaa-app-config`,
  container `shiro-configuration`), edited over RESTCONF - see
  aaa_shiro_config.py for the shared plumbing and its important caveat
  about which HTTP stack this configuration actually governs;
* the parser's numeric/regex limits (max-header-length, max-role-length,
  max-user-length, max-roles-per-user, allowed-chars) are a ConfigAdmin PID,
  `org.opendaylight.aaa.shiro.oauth2proxyheaderparser`, edited by writing
  its `.cfg` file directly (not via the Karaf shell - see
  set_parser_config's docstring for why).
"""

import logging
import time

import requests

from libraries import aaa_shiro_config as shiro
from libraries import infra
from libraries import templated_requests
from libraries.variables import variables

ODL_IP = variables.ODL_IP

REALM_KEY = "oauth2ProxyHeaderRealm"
REALM_CLASS = "org.opendaylight.aaa.shiro.realm.Oauth2ProxyHeaderRealm"
FILTER_KEY = "oauth2ProxyHeaderFilter"
FILTER_CLASS = "org.opendaylight.aaa.shiro.filters.Oauth2ProxyHeaderFilter"

PARSER_CONFIG_PID = "org.opendaylight.aaa.shiro.oauth2proxyheaderparser"
PARSER_CONFIG_DEFAULTS = {
    "max-header-length": "4096",
    "max-role-length": "128",
    "max-user-length": "128",
    "max-roles-per-user": "200",
    # Double backslash, not single: this gets written to a Java Properties-
    # format .cfg file (see _write_parser_config), where a single backslash
    # is itself an escape character. Felix's loader silently drops an
    # unrecognized single-backslash escape like `\-`, corrupting this into
    # `[a-zA-Z0-9_.:-@]` - an unescaped `-` between `:` and `@` is then read
    # as a *range* (':' through '@'), which drops the literal hyphen from
    # the allowed set entirely and breaks every role/username containing
    # one (e.g. "netconf-admin-role").
    "allowed-chars": r"[a-zA-Z0-9_.:\\-@]",
}

USER_HEADER = "X-Forwarded-User"
GROUPS_HEADER = "X-Forwarded-Groups"

log = logging.getLogger(__name__)


def enable_oauth2_proxy_header(
    protected_uri_pattern: str, required_role: str, host: str = ODL_IP
) -> dict:
    """Wires the Oauth2ProxyHeaderFilter/Realm into AAA's shiro configuration,
    guarding `protected_uri_pattern` behind `roles[required_role]`. See
    aaa_shiro_config.enable_realm_and_filter, which does the actual work.

    Returns:
        dict: Prior state, to be passed to `disable_oauth2_proxy_header`.
    """
    return shiro.enable_realm_and_filter(
        REALM_KEY, REALM_CLASS, FILTER_KEY, FILTER_CLASS,
        protected_uri_pattern, required_role, host=host,
    )


def disable_oauth2_proxy_header(state: dict, host: str = ODL_IP):
    """Reverts the changes made by `enable_oauth2_proxy_header`."""
    shiro.disable_realm_and_filter(REALM_KEY, FILTER_KEY, state, host=host)


PARSER_CONFIG_FILE = f"opendaylight/etc/{PARSER_CONFIG_PID}.cfg"


def _write_parser_config(overrides: dict, host: str = ODL_IP):
    """Overwrites the oauth2proxyheaderparser ConfigAdmin PID's `.cfg` file
    with PARSER_CONFIG_DEFAULTS merged with `overrides`.

    Writes the plain properties file directly rather than going through
    the Karaf shell's `config:edit`/`config:property-set`/`config:update`
    - confirmed by hand that the shell path is broken for
    this PID's numeric properties: `config:property-set`'s `value` argument
    is bound to a loosely-typed `Object` field, and Karaf's shell converter
    stores it as a Felix TypedProperties string-literal complete with
    literal surrounding quote characters. That's invisible as long as
    nothing reads the value before a reload re-parses the file correctly,
    but Oauth2ProxyHeaderParserConfigImpl picks it up live - from the same
    ConfigurationEvent dispatch that restarts the aaa-shiro blueprint - and
    promptly fails with `NumberFormatException: For input string: ""4096""`,
    taking the whole parser (and therefore every subsequent request through
    it) down with it until the property is corrected.
    """
    config = PARSER_CONFIG_DEFAULTS | overrides
    content = "\n".join(f"{key} = {value}" for key, value in config.items()) + "\n"
    infra.save_text_to_a_file(PARSER_CONFIG_FILE, content)
    # Felix fileinstall polls periodically rather than reacting immediately;
    # give it a moment to notice and kick off the reconfiguration before
    # probing for it to have settled.
    time.sleep(3)
    shiro.wait_for_restconf_ready(host=host)


def set_parser_config(key: str, value: str, host: str = ODL_IP):
    """Sets a single property of the oauth2proxyheaderparser ConfigAdmin PID,
    restoring every other property to its shipped default."""
    _write_parser_config({key: value}, host=host)


def reset_parser_config(host: str = ODL_IP):
    """Restores every parser config property to its shipped default."""
    _write_parser_config({}, host=host)


def get_with_proxy_headers(
    uri: str,
    username: str | None = None,
    groups: str | None = None,
    expected_code: int | None = None,
    host: str = ODL_IP,
) -> requests.Response:
    """Sends a GET request carrying the given proxy-injected headers.

    Deliberately sends no Authorization/Basic-Auth credentials (auth=None):
    the protected URI is guarded by the Oauth2ProxyHeaderFilter, not
    authcBasic, so ODL is expected to trust these headers as if a reverse
    proxy had already authenticated the caller. templated_requests.
    get_from_uri's own default (admin/admin Basic Auth on every request)
    would defeat that - it's overridden here, not relied on.

    Sent to the JAX-RS RESTCONF endpoint (base_url=shiro.jaxrs_base_url),
    not the Netty one get_from_uri targets by default: only the JAX-RS
    endpoint actually installs AAAShiroFilter, so it's the only one this
    filter/realm configuration has any effect on. See aaa_shiro_config.py.

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
        base_url=shiro.jaxrs_base_url(host),
        auth=None,
    )
