#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

"""Shared helpers for editing AAA's Apache-Shiro `main`/`urls` filter-chain
configuration (module `aaa-app-config`, container `shiro-configuration`) over
RESTCONF.

Used by any suite that needs to wire a custom Shiro realm/filter into the
aaa-shiro blueprint chain - see oauth_proxy_header.py.

Important limitation shared by every such suite: this configuration is only
ever consulted by aaa-shiro's classic Jetty-based `AAAShiroFilter`
(registered under AAA's own `/auth` web context, port 8181 in this
distribution). The project's other, Netty-based RESTCONF northbound
(`odl-restconf-nb`, `protocol/restconf-server`) has its own auth handler
(`AAAShiroPrincipalService`) that only ever builds a Basic-Auth
`UsernamePasswordToken` - confirmed by reading that class's source. Writing
realms/filters/urls here has no effect on that implementation's actual
authentication decision; it only exists so that these suites exercise the
real, RESTCONF-independent configuration surface (and so they start passing
with no further pytest-side changes the moment Java-side wiring, if ever
added, connects a non-Basic-Auth credential into that Netty path).

This is also why every call in this module goes through the JAX-RS RESTCONF
implementation (`odl-restconf-nb-jaxrs`, see jaxrs_base_url/JAXRS_RESTCONF_
ROOT below) rather than the project's usual Netty one: only the former's
web context is set up through AAA's WebContextSecurer (confirmed via
JaxRsEndpoint.java and by decompiling ShiroWebContextSecurer), so it is
currently the only RESTCONF implementation whose authentication is actually
governed by this module's own configuration surface. Both implementations
are backed by the same MD-SAL RestconfServer business logic, so this reads
and writes the exact same YANG-modeled data either way - this only ever
changes which HTTP implementation serves the request, never what data
exists. Safe to rely on unconditionally: every caller of this module already
calls ensure_jaxrs_installed() before anything else (see suite_setup in
test_oauth_proxy_header.py), so nothing here ever needs to tolerate JAX-RS
not yet being installed.

Every `main`/`urls` change goes through a single whole-container read +
whole-container write (get_shiro_configuration/put_shiro_configuration),
never a handful of smaller per-key reads/writes. Two reasons: a keyless
list node isn't a valid RESTCONF PUT target (confirmed by hand: the server
returns 400 for that) so a container-level PUT is required regardless; and
each such write tears down and rebuilds the whole aaa-shiro blueprint
container (and therefore every bean it constructs, including both AAAShiro
Filter-secured web contexts) - so several separate writes for what is
conceptually one configuration change (e.g. "add this realm and reference
it") means several separate, independent restarts in quick succession for
no benefit, which has been confirmed by hand to occasionally leave the
whole aaa-shiro bundle permanently stuck (`Active/Waiting`, via an
unrelated, pre-existing AAA IDM bean-construction defect this module has no
control over). Collapsing each conceptual change into exactly one write is
the only lever available here to minimize how often that gets exercised.
"""

import logging
import time

import requests

from libraries import infra
from libraries import utils
from libraries.variables import variables

log = logging.getLogger(__name__)

ODL_IP = variables.ODL_IP

REALMS_KEY = "securityManager.realms"
DEFAULT_REALM_KEY = "tokenAuthRealm"
DEFAULT_REALM_CLASS = "org.opendaylight.aaa.shiro.realm.TokenAuthRealm"

# odl-restconf-nb-jaxrs is deprecated for removal (see
# OSGiNorthboundJaxrs.java), but is currently the only way to exercise
# Oauth2ProxyHeaderFilter for real without Java changes - see the module
# docstring above for why.
JAXRS_PORT = 8181
JAXRS_RESTCONF_ROOT = "rests"
JAXRS_FEATURE = "odl-restconf-nb-jaxrs"

SHIRO_CONFIG_URI = f"{JAXRS_RESTCONF_ROOT}/data/aaa-app-config:shiro-configuration"


def jaxrs_base_url(host: str = ODL_IP) -> str:
    return f"http://{host}:{JAXRS_PORT}"


def admin_auth() -> requests.auth.HTTPBasicAuth:
    # These config-management calls hit the ordinary admin-protected
    # RESTCONF tree (guarded by the catch-all authcBasic entry), not
    # whatever resource a given suite is guarding with its own filter, so
    # they need the same admin/admin credentials the rest of the suite uses.
    return requests.auth.HTTPBasicAuth("admin", "admin")


def is_data_missing(response: requests.Response) -> bool:
    """True for RFC 8040's "data-missing" error on a GET of something that
    doesn't exist yet. Per RFC 8040 Appendix A.1 this maps to HTTP 409
    (Conflict), not 404 - so status code alone can't tell "not configured
    yet" apart from a real conflict; the error-tag in the body can.
    """
    if response.status_code != 409:
        return False
    try:
        errors = response.json()["errors"]["error"]
    except (ValueError, KeyError, IndexError):
        return False
    return any(error.get("error-tag") == "data-missing" for error in errors)


def wait_for_restconf_ready(host: str = ODL_IP):
    """Blocks until the JAX-RS RESTCONF web context has settled after a
    shiro-configuration write (or a fresh install of odl-restconf-nb-jaxrs
    itself - see ensure_jaxrs_installed).

    A single successful probe isn't enough: the rebuild can briefly answer
    again and then drop the connection a second time (confirmed by hand),
    so this requires two consecutive successes a couple of seconds apart
    before considering it settled. Built on utils.wait_until_function_pass
    (the project's own generic retry helper, used throughout the rest of
    this test suite) rather than a bespoke retry loop.
    """

    def _probe():
        requests.get(
            f"{jaxrs_base_url(host)}/.well-known/host-meta",
            auth=admin_auth(),
            timeout=5,
        ).raise_for_status()

    def _probe_twice():
        _probe()
        time.sleep(2)
        _probe()

    utils.wait_until_function_pass(30, 1, _probe_twice)


def ensure_jaxrs_installed(host: str = ODL_IP):
    """Installs odl-restconf-nb-jaxrs if not already installed, and waits
    for its web context (and the AAAShiroFilter/auth chain guarding it) to
    actually settle before returning.

    Idempotent: feature:install on an already-installed feature is a no-op,
    so every suite needing the JAX-RS endpoint can call this in its own
    setup without coordinating with any other suite. Left installed for the
    rest of the session rather than uninstalled afterwards, since it only
    adds a second, additive RESTCONF endpoint and doesn't affect anything
    using the Netty one.

    The wait is required, not an optimization: every other function in this
    module talks to JAX-RS exclusively, and a request landing immediately
    after this feature installs (before its own web context/auth chain has
    settled) comes back as a bare 401 - confirmed by hand. Every caller
    already relies on this happening before its first read/write.
    """
    infra.execute_karaf_command(f"feature:install {JAXRS_FEATURE}", host=host)
    wait_for_restconf_ready(host=host)


def get_shiro_configuration(host: str = ODL_IP) -> dict:
    """Reads the whole shiro-configuration container (`main` and `urls`
    lists together), or empty lists if it isn't configured yet.

    The aaa-app-config:shiro-configuration container isn't actually
    bootstrapped into the datastore from the shipped default XML in this
    environment (only copied under etc/, never imported) - yet admin/admin
    auth already works via AAA's hardcoded in-code default realm chain
    (tokenAuthRealm, backed by the H2/IDMLight store, per the shipped
    aaa-app-config.xml's own `$tokenAuthRealm` default). Callers that add a
    new realm/filter must therefore handle an empty `main` list specially:
    extending that chain means first defining `tokenAuthRealm` as a `main`
    entry themselves (it isn't one yet) - referencing it by name in
    `securityManager.realms` without defining it first is fatal, not just
    ignored: Shiro's Ini loader requires every bean to be defined by an
    earlier entry than anything that references it, and an undefined
    reference crashes the whole aaa-shiro blueprint container, taking
    RESTCONF down with it (confirmed by hand against a live instance:
    `UnresolveableReferenceException: The object with id [tokenAuthRealm]
    has not yet been defined`).
    """
    url = f"{jaxrs_base_url(host)}/{SHIRO_CONFIG_URI}"
    response = requests.get(
        url, headers={"Accept": "application/yang-data+json"}, auth=admin_auth()
    )
    if is_data_missing(response):
        return {"main": [], "urls": []}
    response.raise_for_status()
    container = response.json().get("aaa-app-config:shiro-configuration", {})
    return {"main": container.get("main", []), "urls": container.get("urls", [])}


def put_shiro_configuration(main: list[dict], urls: list[dict], host: str = ODL_IP):
    """Overwrites the whole `shiro-configuration` container with exactly
    `main` and `urls`, in that order, in a single write - and therefore a
    single aaa-shiro blueprint restart. See this module's own docstring for
    why collapsing a whole conceptual change into one write like this
    matters here.

    A container-level PUT like this is also the only way to write a `urls`
    entry at all, not just the preferred one: every `urls` pair-key is a
    Shiro ant-style URI pattern, so it always contains at least one literal
    `/`, which RFC 8040 requires percent-encoding as `%2F` in a single-entry
    PUT's URI segment - and Jetty 9.4's default UriCompliance mode rejects
    any request path containing `%2F` outright as "ambiguous", with a bare
    400 and no response body (confirmed by hand). Writing the whole
    container instead means the key only ever appears inside the JSON body,
    never the URL path, so it never hits that check.
    """
    url = f"{jaxrs_base_url(host)}/{SHIRO_CONFIG_URI}"
    payload = {"aaa-app-config:shiro-configuration": {"main": main, "urls": urls}}
    response = requests.put(
        url,
        json=payload,
        headers={"Content-Type": "application/yang-data+json"},
        auth=admin_auth(),
    )
    response.raise_for_status()
    wait_for_restconf_ready(host=host)


def enable_realm_and_filter(
    realm_key: str,
    realm_class: str,
    filter_key: str,
    filter_class: str,
    protected_uri_pattern: str,
    required_role: str,
    host: str = ODL_IP,
) -> dict:
    """Wires one (realm, filter) bean pair into `main` and one `urls` entry
    guarding `protected_uri_pattern` behind `roles[required_role]`, in a
    single combined write. Takes the bean names/classes as plain arguments
    rather than hardcoding Oauth2ProxyHeaderFilter/Oauth2ProxyHeaderRealm
    specifically, so any other suite wiring a custom Shiro realm/filter the
    same way can reuse it as-is.

    Only adds a realm/filter and one `urls` entry; the existing catch-all
    entries (e.g. `authcBasic` used by admin/admin elsewhere in the suite)
    are left untouched.

    Returns:
        dict: Prior state, to be passed to `disable_realm_and_filter`.
    """
    config = get_shiro_configuration(host=host)
    main = config["main"]
    urls = config["urls"]

    realms_entry = next(
        (entry for entry in main if entry["pair-key"] == REALMS_KEY), None
    )
    if realms_entry is None:
        # Not bootstrapped yet - see get_shiro_configuration's docstring.
        # tokenAuthRealm must be defined before anything references it, and
        # nothing does yet in this branch, so appending it here is safe.
        main.append({"pair-key": DEFAULT_REALM_KEY, "pair-value": DEFAULT_REALM_CLASS})
        previous_realms = f"${DEFAULT_REALM_KEY}"
    else:
        previous_realms = realms_entry["pair-value"]

    # The new realm/filter beans must be defined earlier in `main` than
    # whatever references them: Shiro's Ini loader requires it (an
    # undefined/forward reference crashes the whole aaa-shiro blueprint
    # container, confirmed by hand). Inserting them right before
    # securityManager.realms - or appending, if that entry doesn't exist
    # yet either - guarantees that regardless.
    insert_at = main.index(realms_entry) if realms_entry is not None else len(main)
    main[insert_at:insert_at] = [
        {"pair-key": realm_key, "pair-value": realm_class},
        {"pair-key": filter_key, "pair-value": filter_class},
    ]

    new_realms_value = f"{previous_realms}, ${realm_key}"
    if realms_entry is None:
        main.append({"pair-key": REALMS_KEY, "pair-value": new_realms_value})
    else:
        realms_entry["pair-value"] = new_realms_value

    urls = [entry for entry in urls if entry["pair-key"] != protected_uri_pattern]
    urls.append(
        {
            "pair-key": protected_uri_pattern,
            "pair-value": f"{filter_key}, roles[{required_role}]",
        }
    )

    put_shiro_configuration(main, urls, host=host)
    return {"previous_realms": previous_realms, "urls_pair_key": protected_uri_pattern}


def disable_realm_and_filter(
    realm_key: str, filter_key: str, state: dict, host: str = ODL_IP
):
    """Reverts the changes made by `enable_realm_and_filter`, in a single
    combined write."""
    config = get_shiro_configuration(host=host)
    main = [
        entry
        for entry in config["main"]
        if entry["pair-key"] not in (filter_key, realm_key)
    ]
    for entry in main:
        if entry["pair-key"] == REALMS_KEY:
            entry["pair-value"] = state["previous_realms"]
            break
    urls = [
        entry for entry in config["urls"] if entry["pair-key"] != state["urls_pair_key"]
    ]
    put_shiro_configuration(main, urls, host=host)
