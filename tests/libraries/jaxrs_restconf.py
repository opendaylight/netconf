#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

"""Lifecycle helpers for ODL's JAX-RS RESTCONF implementation
(`odl-restconf-nb-jaxrs`) - installing it and waiting for it to settle.
This module is for any suite that needs the JAX-RS endpoint for some of
its own test cases - it isn't meant to replace the project's default
RESTCONF endpoint globally; that's configured via its own RESTCONF_PORT/
RESTCONF_ROOT in the global variables.py config.

odl-restconf-nb-jaxrs is deprecated for removal, but it's currently the
only RESTCONF implementation whose web context is wired through AAA's
authentication - the project's usual Netty-based implementation
authenticates differently and never consults any AAA-configured realm or
filter at all.
"""

import logging

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
    """Builds the JAX-RS RESTCONF base URL for a given node.

    Args:
        host (str): Node to target.

    Returns:
        str: JAX-RS RESTCONF base URL, without a trailing slash.
    """
    return f"http://{host}:{PORT}"


def wait_for_ready(host: str = ODL_IP, retry_count: int = 30):
    """Blocks until RESTCONF answers, e.g. after installing it or after a
    configuration write that restarts it. Probes `/.well-known/host-meta`,
    a standard discovery resource that's always present regardless of
    configuration.

    Args:
        host (str): Node to probe.
        retry_count (int): How many attempts to make, one second apart,
            before giving up. A caller waiting out a full Karaf restart rather
            than a quick internal reload should pass a larger budget.
    """

    def _probe():
        templated_requests.get_from_uri(
            ".well-known/host-meta",
            http_timeout=5,
            host=host,
            base_url=base_url(host),
        )

    utils.wait_until_function_pass(retry_count, 1, _probe)


def ensure_installed(host: str = ODL_IP):
    """Installs odl-restconf-nb-jaxrs if not already installed, and waits
    for its web context to actually settle before returning.

    Idempotent: installing an already-installed feature is a no-op, so
    this can be called from any suite's own setup without coordinating
    with others. Left installed afterwards rather than uninstalled, since
    it only adds a second, additive RESTCONF endpoint alongside the
    default one. The wait matters: a request landing immediately after
    install, before the web context has settled, comes back as a bare 401.

    Args:
        host (str): Node to install on.
    """
    infra.execute_karaf_command(f"feature:install {FEATURE}", host=host, verify=True)
    wait_for_ready(host=host)
