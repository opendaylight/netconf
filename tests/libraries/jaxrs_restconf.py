#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

"""Lifecycle helpers for ODL's JAX-RS RESTCONF implementation
(`odl-restconf-nb-jaxrs`), shared by every suite that depends on it - not
just the ones editing AAA's Shiro `main`/`urls` configuration
(aaa_shiro_config.py), but also e.g. the Bearer JWT suites, which configure
a different realm PID entirely but still need this exact same endpoint
installed and settled first.

odl-restconf-nb-jaxrs is deprecated for removal (see OSGiNorthboundJaxrs.
java), but is currently the only RESTCONF implementation whose web context
is wired through AAA's WebContextSecurer (confirmed via JaxRsEndpoint.java
and by decompiling ShiroWebContextSecurer) - the project's usual Netty-based
one (`odl-restconf-nb`) has its own auth handler (AAAShiroPrincipalService)
that only ever builds a Basic-Auth UsernamePasswordToken, so it never
consults any AAA-configured realm/filter at all. Both implementations are
backed by the same MD-SAL RestconfServer business logic, so using this one
only ever changes which HTTP implementation serves the request, never what
data exists.
"""

import logging
import time

from libraries import infra
from libraries import templated_requests
from libraries import utils
from libraries.variables import variables

log = logging.getLogger(__name__)

ODL_IP = variables.ODL_IP

PORT = 8181
RESTCONF_ROOT = "rests"
FEATURE = "odl-restconf-nb-jaxrs"


def base_url(host: str = ODL_IP) -> str:
    return f"http://{host}:{PORT}"


def wait_for_ready(host: str = ODL_IP):
    """Blocks until the JAX-RS RESTCONF web context has settled after an
    AAA configuration write (or a fresh install of odl-restconf-nb-jaxrs
    itself - see ensure_installed).

    A single successful probe isn't enough: the rebuild can briefly answer
    again and then drop the connection a second time (confirmed by hand),
    so this requires two consecutive successes a couple of seconds apart
    before considering it settled. Built on utils.wait_until_function_pass
    (the project's own generic retry helper, used throughout the rest of
    this test suite) rather than a bespoke retry loop.

    Probes `/.well-known/host-meta` - RFC 8040's standardized discovery
    resource, guaranteed to exist on any compliant RESTCONF server
    regardless of which YANG modules are mounted or what any datastore
    currently contains. It's also registered as its own JAX-RS web context
    (confirmed in the karaf log), guarded by the same AAAShiroFilter as
    every other JAX-RS context, so probing it exercises the same
    filter/web-context wiring an AAA configuration rewrite just rebuilt.
    """

    def _probe():
        templated_requests.get_from_uri(
            ".well-known/host-meta",
            http_timeout=5,
            host=host,
            base_url=base_url(host),
        )

    def _probe_twice():
        _probe()
        time.sleep(2)
        _probe()

    utils.wait_until_function_pass(30, 1, _probe_twice)


def ensure_installed(host: str = ODL_IP):
    """Installs odl-restconf-nb-jaxrs if not already installed, and waits
    for its web context (and the AAAShiroFilter/auth chain guarding it) to
    actually settle before returning.

    Idempotent: feature:install on an already-installed feature is a no-op,
    so every suite needing the JAX-RS endpoint can call this in its own
    setup without coordinating with any other suite. Left installed for the
    rest of the session rather than uninstalled afterwards, since it only
    adds a second, additive RESTCONF endpoint and doesn't affect anything
    using the Netty one.

    The wait is required, not an optimization: a request landing immediately
    after this feature installs (before its own web context/auth chain has
    settled) comes back as a bare 401 - confirmed by hand. Every caller
    already relies on this happening before its first read/write.
    """
    infra.execute_karaf_command(f"feature:install {FEATURE}", host=host)
    wait_for_ready(host=host)
