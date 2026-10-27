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
  _write_parser_config's docstring for why).
"""

import logging
import time

import requests

from libraries import aaa_shiro_config as shiro
from libraries import infra
from libraries import jaxrs_restconf
from libraries import templated_requests
from libraries.variables import variables

ODL_IP = variables.ODL_IP

REQUIRED_ROLE = "netconf-admin-role"

# This suite's whole shiro-configuration: the Oauth2ProxyHeaderFilter/Realm,
# guarding "/**/data/ietf-yang-library:yang-library**" behind
# `roles[netconf-admin-role]`. Static, not templated or merged with
# whatever `main`/`urls` might already contain - trying to merge with an
# unknown prior state is itself a source of hard-to-predict bugs, so this
# suite owns its whole configuration outright, as one fixed file. Setting it
# saves whatever was there before (see aaa_shiro_config.
# read_shiro_configuration) and uploads this file; the caller restores that
# saved state exactly afterwards, via aaa_shiro_config.
# restore_shiro_configuration directly - not a patch reconstructed after
# the fact.
_SHIRO_CONFIGURATION_TEMPLATE_DIR = "variables/oauth/proxy_header"

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


def set_oauth2_proxy_header_configuration(host: str = ODL_IP) -> dict | None:
    """Saves whatever shiro-configuration is there before this suite starts,
    then uploads its own whole configuration from a static file - see
    _SHIRO_CONFIGURATION_TEMPLATE_DIR above.

    Args:
        host (str): Node to write to.

    Returns:
        dict | None: Prior state, to be passed to
            `aaa_shiro_config.restore_shiro_configuration`.
    """
    previous = shiro.read_shiro_configuration(host=host)
    templated_requests.put_templated_request(
        _SHIRO_CONFIGURATION_TEMPLATE_DIR,
        mapping={},
        base_url=jaxrs_restconf.base_url(host),
        host=host,
    )
    jaxrs_restconf.wait_for_ready(host=host)
    return previous


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

    Args:
        overrides (dict): Properties to override on top of
            PARSER_CONFIG_DEFAULTS.
        host (str): Node to write to.
    """
    config = PARSER_CONFIG_DEFAULTS | overrides
    content = "\n".join(f"{key} = {value}" for key, value in config.items()) + "\n"
    infra.save_text_to_a_file(PARSER_CONFIG_FILE, content)
    # Felix fileinstall polls periodically rather than reacting immediately;
    # give it a moment to notice and kick off the reconfiguration before
    # probing for it to have settled.
    time.sleep(3)
    jaxrs_restconf.wait_for_ready(host=host)


def set_parser_config(overrides: dict, host: str = ODL_IP):
    """Rewrites the oauth2proxyheaderparser ConfigAdmin PID to its shipped
    defaults with `overrides` applied on top - not a patch of the current
    file, a full rewrite, so every property not named in `overrides` ends up
    at its shipped default regardless of whatever was there before.

    Args:
        overrides (dict): Properties to override on top of the shipped
            defaults.
        host (str): Node to write to.
    """
    _write_parser_config(overrides, host=host)


def reset_parser_config(host: str = ODL_IP):
    """Rewrites the oauth2proxyheaderparser ConfigAdmin PID to exactly its
    shipped defaults.

    Args:
        host (str): Node to write to.
    """
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

    Sent to the JAX-RS RESTCONF endpoint (base_url=jaxrs_restconf.base_url),
    not the Netty one get_from_uri targets by default: only the JAX-RS
    endpoint actually installs AAAShiroFilter, so it's the only one this
    filter/realm configuration has any effect on. See jaxrs_restconf.py and
    aaa_shiro_config.py.

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
