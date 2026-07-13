#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Sets up a multi-node ODL cluster on a single test host by giving each
# member its own loopback alias (127.0.0.x) and its own copy of the Karaf
# distribution, then wiring them together with ODL's own
# bin/configure_cluster.sh.
#

import logging

from libraries import infra
from libraries.variables import variables

CLUSTER_MEMBER_IPS = variables.CLUSTER_MEMBER_IPS

log = logging.getLogger(__name__)

# Config files whose bind address defaults to every interface (0.0.0.0),
# which only one member per host can hold. Value is the key as it appears
# in the file, whether currently active, commented out, or not present yet
# (e.g. org.opendaylight.netconf.ssh.cfg isn't shipped; Felix ConfigAdmin
# falls back to its metatype default of 0.0.0.0 until the file exists).
#
# Note: pax-web-jetty actually starts TWO separate connectors on :8181 --
# "jetty-default" from etc/jetty.xml (resolves ${jetty.host} as a plain JVM
# system property, hence the etc/system.properties entry below) and its own
# "default" HttpService connector. That second one is NOT controlled by
# org.osgi.service.http.host (verified by decompiling pax-web-runtime-8.0.34:
# that key doesn't exist in this version at all) but by
# org.ops4j.pax.web.listening.addresses (comma-separated, defaults to
# "0.0.0.0"). Both connectors must be pinned, or whichever binds its
# specific address first blocks the other's 0.0.0.0 bind on the same port.
_MEMBER_BIND_ADDRESS_SETTINGS = (
    ("etc/org.apache.karaf.shell.cfg", "sshHost"),
    ("etc/org.opendaylight.restconf.nb.rfc8040.cfg", "bind-address"),
    ("etc/org.opendaylight.netconf.topology.callhome.cfg", "host"),
    ("etc/system.properties", "jetty.host"),
    ("etc/org.ops4j.pax.web.cfg", "org.ops4j.pax.web.listening.addresses"),
    ("etc/org.opendaylight.netconf.ssh.cfg", "bindingAddress"),
)


def _prepare_member_directories():
    """Stages one fresh Karaf distribution copy per cluster member.

    Member 1 reuses the `opendaylight` directory staged by the build.
    Every other member gets a clean copy of it, replacing any leftovers
    from a previous run.

    Args:
        None

    Returns:
        None
    """
    dirs = get_cluster_dirs()
    infra.shell(f"mv opendaylight {dirs[0]}")
    for target_dir in dirs[1:]:
        infra.shell(f"rm -rf {target_dir}")
        infra.copy_dir(dirs[0], target_dir)
    return dirs


def _ensure_pekko_conf_exists(cwd: str):
    """Materializes configuration/initial/pekko.conf if ODL never booted here.

    A fresh distribution copy only ships the shipped default under
    system/.../sal-clustering-config/<version>/sal-clustering-config-<version>-pekkoconf.xml;
    configuration/initial/pekko.conf (the file actually read at runtime) is
    normally materialized either by ODL itself on first boot or by
    bin/configure_cluster.sh's own bootstrap step. Since member setup runs
    before ODL is started, and cluster wiring may be skipped, do the same
    fallback copy here so there's always a file to edit.

    Args:
        cwd (str): Member's distribution directory.

    Returns:
        None
    """
    infra.shell(
        "mkdir -p configuration/initial && "
        "test -f configuration/initial/pekko.conf || "
        "cp system/org/opendaylight/controller/sal-clustering-config/*/"
        "sal-clustering-config-*-pekkoconf.xml configuration/initial/pekko.conf",
        cwd=cwd,
    )


def _pin_pekko_canonical_hostname(cwd: str, member_ip: str):
    """Pins pekko's own remote listener address to this member's IP.

    Every fresh member copy inherits the same pekko.conf, whose
    `canonical.hostname` defaults to 127.0.0.1 for all of them. Since that
    is the address artery's TCP transport actually binds on port 2550,
    every member past the first fails to start with "Address already in
    use". This only fixes the member's own bind address; seed-nodes/roles
    still need bin/configure_cluster.sh (see configure_member_cluster) to
    actually join a cluster.

    Args:
        cwd (str): Member's distribution directory.
        member_ip (str): Address for this member's pekko remote transport.

    Returns:
        None
    """
    _ensure_pekko_conf_exists(cwd)
    infra.shell(
        "sed -i -E "
        f"'s/(canonical\\.hostname[ ]*=[ ]*).*/\\1\"{member_ip}\"/' "
        "configuration/initial/pekko.conf",
        cwd=cwd,
    )


def _configure_member_network(cwd: str, member_ip: str):
    """Rebinds a member's wildcard (0.0.0.0) endpoints to its own address.

    Karaf SSH, RESTCONF and Call Home all default to binding every
    interface, which only one process per host can do. Pinning each member
    to its own loopback alias lets every member keep the single-node ports
    unchanged. Pekko's remote transport is handled separately since it
    binds a specific address (127.0.0.1) rather than a wildcard.

    Args:
        cwd (str): Member's distribution directory.
        member_ip (str): Address to bind this member's endpoints to.

    Returns:
        None
    """
    for file_path, key in _MEMBER_BIND_ADDRESS_SETTINGS:
        infra.shell(
            f"touch {file_path} && "
            f"sed -i -E 's/^#?{key}[ ]*=.*/{key} = {member_ip}/' {file_path} && "
            f"grep -q '^{key}[ ]*=' {file_path} || "
            f"echo '{key} = {member_ip}' >> {file_path}",
            cwd=cwd,
        )
    _pin_pekko_canonical_hostname(cwd, member_ip)


def _fix_configure_cluster_script(cwd: str):
    """Works around a quoting problems in the shipped bin/configure_cluster.sh.

    `CONTROLLERIPS=( "${CONTROLLER_LIST//,/ }" )` keeps its expansion quoted,
    so bash never splits it into multiple array elements: CONTROLLERIPS ends
    up with a single element containing all IPs space-joined together (e.g.
    "127.0.0.1 127.0.0.2 127.0.0.3"), regardless of whether the seed list was
    passed comma- or space-separated. Every member then gets that whole
    string written into pekko.conf as its own hostname, which pekko fails to
    bind to. Dropping the inner quotes restores the word splitting the script
    already relies on elsewhere (e.g. `for ip in "${CONTROLLERIPS[@]}"`).

    Args:
        cwd (str): Member's distribution directory.

    Returns:
        None
    """
    infra.shell(
        "sed -i 's#CONTROLLERIPS=( \"\\${CONTROLLER_LIST//,/ }\" )#"
        "CONTROLLERIPS=( \\${CONTROLLER_LIST//,/ } )#' bin/configure_cluster.sh",
        cwd=cwd,
    )


def _configure_member_cluster(cwd: str, index: int, member_ips: list[str]):
    """Wires a member into the pekko cluster via ODL's own tooling.

    Runs bin/configure_cluster.sh, which points this member's pekko.conf
    seed-nodes at every member and adds it as a replica in module-shards.conf.

    Args:
        cwd (str): Member's distribution directory.
        index (int): This member's 1-based position in member_ips.
        member_ips (list[str]): Address of every cluster member, in order.

    Returns:
        None
    """
    _fix_configure_cluster_script(cwd)
    infra.shell(f"./bin/configure_cluster.sh {index} {','.join(member_ips)}", cwd=cwd)


def get_member_dir(index: int) -> str:
    """Distribution directory for cluster member `index`.

    Args:
        index (int): Member position.

    Returns:
        str: Path to the member's Karaf distribution, relative to cwd.
    """
    return f"opendaylight-member-{index+1}"


def get_cluster_dirs() -> list[str]:
    return [get_member_dir(index) for index in range(len(CLUSTER_MEMBER_IPS))]


def setup_cluster():
    _prepare_member_directories()
    cluster_dirs = get_cluster_dirs()
    for index, (cwd, member_ip) in enumerate(zip(cluster_dirs, CLUSTER_MEMBER_IPS), start=1):
        _configure_member_network(cwd, member_ip)
        _configure_member_cluster(cwd, index, CLUSTER_MEMBER_IPS)


def start_cluster(
    features: list[str]
):
    """Stages, configures and starts an ODL cluster.

    All members are launched before any of them is awaited: each member's
    pekko actor system needs its peers reachable to finish joining the
    cluster and log "System ready", so starting members one at a time and
    waiting on each in turn would deadlock -- the first member would sit
    retrying its seed nodes forever since nothing else has been started yet.

    Args:
        features (list[str]): Karaf features to boot on every member.
        member_ips (list[str]): One loopback alias per member.

    Returns:
        None
    """
    for cwd in get_cluster_dirs():
        infra.start_odl_with_features(features, cwd=cwd)

def wait_cluter_ready(timeout=600):
    """
    timeout (int): Seconds to wait for each member's "System ready".
    """
    for cwd in get_cluster_dirs():
        infra.wait_for_odl_ready(cwd, timeout=timeout)
