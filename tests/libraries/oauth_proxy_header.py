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

* the filter/realm chain is configuration data, edited over RESTCONF;
* the parser's numeric/regex limits (max-header-length, max-role-length,
  max-user-length, max-roles-per-user, allowed-chars) are a separate
  ConfigAdmin PID, edited via the Karaf shell's config:edit/property-set/
  update - not by rewriting its backing `.cfg` file directly, which relies
  on Felix fileinstall noticing the change by polling and has been
  confirmed by hand to sometimes miss a rewrite outright, even across
  several retries (likely related to `felix.fileinstall.enableConfigSave`,
  which makes that file bidirectional: fileinstall also writes
  ConfigAdmin-originated changes back to it, which can race with its own
  change detection). Going through ConfigurationAdmin directly via the
  shell sidesteps file-watching entirely.
"""

import logging

import requests

from libraries import aaa_shiro_config
from libraries import infra
from libraries import jaxrs_restconf
from libraries import templated_requests
from libraries.variables import variables

ODL_IP = variables.ODL_IP

# This suite's whole shiro-configuration, kept as one static file rather
# than built up from pieces or merged with whatever's already configured -
# merging onto an unknown prior state is itself a source of hard-to-predict
# bugs, so this suite owns its whole configuration outright. Whatever was
# configured before this suite starts must be saved first and restored
# exactly afterwards.
_SHIRO_CONFIGURATION_TEMPLATE_DIR = "variables/oauth/proxy_header"

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


def set_oauth2_proxy_header_configuration(host: str = ODL_IP) -> dict | None:
    """Saves whatever shiro-configuration is there before this suite starts,
    then uploads a fixed configuration that sets up OAuth2 Proxy Header
    authentication.

    Args:
        host (str): Node to write to.

    Returns:
        dict | None: Prior state, to be restored afterwards.
    """
    previous_filter_id = aaa_shiro_config.get_restconf_aaashiro_filter_id(host=host)
    previous = aaa_shiro_config.read_shiro_configuration(host=host)
    templated_requests.put_templated_request(
        _SHIRO_CONFIGURATION_TEMPLATE_DIR,
        mapping={},
        base_url=jaxrs_restconf.base_url(host),
        host=host,
    )
    # Writing shiro-configuration alone doesn't rebuild aaa-shiro's live
    # filter chain - only a genuine ConfigAdmin content change does, so this
    # is what actually triggers the reload being waited for below.
    # previous_filter_id has to be captured up here, before the PUT above,
    # not inside _write_parser_config: on a truly fresh boot that PUT itself
    # can race with a reload of its own, so a baseline taken only right
    # before the parser-config write can already be stale (confirmed by
    # hand - capturing it after the PUT hung here for the full wait budget).
    _write_parser_config({}, host=host)
    aaa_shiro_config.wait_until_restconf_filter_reloads(previous_filter_id, host=host)
    return previous


def _write_parser_config(overrides: dict, host: str = ODL_IP):
    """Rewrites the oauth2proxyheaderparser ConfigAdmin PID to a preset list
    of default values, kept in this module, merged with `overrides` - via
    the Karaf shell's config:edit/property-set/update, going straight
    through ConfigurationAdmin rather than rewriting the PID's backing
    `.cfg` file (confirmed by hand to be fast - reload completes within a
    few seconds - and reliable, unlike the file-rewrite approach).

    Args:
        overrides (dict): Properties to override on top of the defaults.
        host (str): Node to write to.
    """
    config = PARSER_CONFIG_DEFAULTS | overrides
    infra.execute_karaf_command(f"config:edit {PARSER_CONFIG_PID}", host=host)
    for key, value in config.items():
        infra.execute_karaf_command(f'config:property-set {key} "{value}"', host=host)
    infra.execute_karaf_command("config:update", host=host)


def set_parser_config(overrides: dict, host: str = ODL_IP):
    """Rewrites the oauth2proxyheaderparser ConfigAdmin PID using
    `overrides` - not a patch of the current file, a full rewrite, so any
    property not named in `overrides` is set to its default instead,
    regardless of whatever was there before.

    Args:
        overrides (dict): Properties to set; any property not named here
            is set to its default instead.
        host (str): Node to write to.
    """
    previous_filter_id = aaa_shiro_config.get_restconf_aaashiro_filter_id(host=host)
    _write_parser_config(overrides, host=host)
    aaa_shiro_config.wait_until_restconf_filter_reloads(previous_filter_id, host=host)


def reset_parser_config(host: str = ODL_IP):
    """Rewrites the oauth2proxyheaderparser ConfigAdmin PID to exactly the
    preset list of default values kept in this module.

    Args:
        host (str): Node to write to.
    """
    previous_filter_id = aaa_shiro_config.get_restconf_aaashiro_filter_id(host=host)
    _write_parser_config({}, host=host)
    aaa_shiro_config.wait_until_restconf_filter_reloads(previous_filter_id, host=host)


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
    reverse proxy had already authenticated the caller. A default
    admin/admin Basic Auth credential would otherwise be sent on every
    request - that default is overridden here, not relied on.

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
