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
ODL_IP = variables.ODL_IP

log = logging.getLogger(__name__)

# Whether the current pytest session is running the cluster topology.
# Set once via set_is_cluster_run(), from conftest's cluster setup fixture.
_is_cluster_run = False


def set_is_cluster_run(value: bool):
    """Records whether this pytest session is running the cluster topology.

    Args:
        value (bool): True if this session is running cluster-marked tests.

    Returns:
        None
    """
    global _is_cluster_run
    _is_cluster_run = value


def is_cluster_run() -> bool:
    """Whether this pytest session is running the cluster topology.

    Args:
        None

    Returns:
        bool: True for a cluster session, False for standalone.
    """
    return _is_cluster_run


def active_nodes() -> list[str]:
    """Every ODL node address in play for this pytest session.

    Generic building block for anything that needs to act on "all nodes
    currently under test" -- not just Karaf logging. Single-node code paths
    still work unmodified since standalone sessions get a one-element list.

    Args:
        None

    Returns:
        list[str]: CLUSTER_MEMBER_IPS for a cluster session, otherwise a
            single-element list containing just ODL_IP.
    """
    return CLUSTER_MEMBER_IPS if is_cluster_run() else [ODL_IP]


# Config files whose bind address defaults to every interface (0.0.0.0),
# which only one member per host can hold. Value is the key as it appears
# in the file, whether currently active, commented out, or not present yet
# (e.g. org.opendaylight.netconf.ssh.cfg isn't shipped; Felix ConfigAdmin
# falls back to its metatype default of 0.0.0.0 until the file exists).
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

    Member 1 is (re)staged from the `opendaylight` directory staged by the
    build; every other member gets a clean copy of it. Any member directory
    left over from a previous run is removed first so re-runs on the same
    workspace start clean (the build only refreshes `opendaylight`, not the
    per-member copies).

    Args:
        None

    Returns:
        None
    """
    dirs = get_cluster_dirs()
    # Guard for rm -rf calls below; every entry must be a plain, non-empty,
    # relative member directory name; assert makes the invariant explicit
    # to prevent a recursive delete on an empty, absolute or parent path
    # in any future refactor.
    assert all(
        name and name.startswith("opendaylight-member-") and "/" not in name
        for name in dirs
    ), f"unexpected cluster member directory name in {dirs}"
    infra.shell(f"rm -rf {dirs[0]}")
    infra.shell(f"mv opendaylight {dirs[0]}")
    for target_dir in dirs[1:]:
        infra.shell(f"rm -rf {target_dir}")
        infra.copy_dir(dirs[0], target_dir)
    return dirs


def _ensure_pekko_conf_exists(cwd: str):
    """Ensures configuration/initial/pekko.conf exists before ODL starts.

    Because the runtime pekko.conf is normally generated on ODL's first boot,
    this copies the default configuration template into place so it is
    available to edit during setup.

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
    bind to.

    Args:
        cwd (str): Member's distribution directory.

    Returns:
        None
    """
    infra.shell(
        'sed -i \'s#CONTROLLERIPS=( "\\${CONTROLLER_LIST//,/ }" )#'
        "CONTROLLERIPS=( \\${CONTROLLER_LIST//,/ } )#' bin/configure_cluster.sh",
        cwd=cwd,
    )


def _configure_member_cluster(cwd: str, index: int, member_ips: list[str]):
    """Wires a member into the pekko cluster via custom shell script.

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
    """Returns distribution directories for every configured cluster member.

    Args:
        None

    Returns:
        list[str]: Path to each member's Karaf distribution, in member order.
    """
    return [get_member_dir(index) for index in range(len(CLUSTER_MEMBER_IPS))]


def setup_cluster():
    """Configures every cluster member, without starting them.

    Copies one Karaf distribution per entry in CLUSTER_MEMBER_IPS, rebinds
    each member's endpoints to its own address, and wires every member into
    the pekko cluster via ODL's own script for configuring cluster.

    Args:
        None

    Returns:
        None
    """
    _prepare_member_directories()
    cluster_dirs = get_cluster_dirs()
    for index, (cwd, member_ip) in enumerate(
        zip(cluster_dirs, CLUSTER_MEMBER_IPS), start=1
    ):
        _configure_member_network(cwd, member_ip)
        _configure_member_cluster(cwd, index, CLUSTER_MEMBER_IPS)
    set_is_cluster_run(True)


def start_cluster(features: list[str]):
    """Starts ODL cluster members with a specified set of features.

    All members are launched before any of them is awaited: each member's
    pekko actor system needs its peers reachable to finish joining the
    cluster and log "System ready", so starting members one at a time and
    waiting on each in turn would deadlock -- the first member would sit
    retrying its seed nodes forever since nothing else has been started yet.

    Args:
        features (list[str]): Karaf features to boot on every member.

    Returns:
        None
    """
    for cwd in get_cluster_dirs():
        infra.start_odl_with_features(features, cwd=cwd)


def wait_cluter_ready(timeout=600):
    """Blocks until every member logs "System ready" in the given distribution dir.

    Args:
        timeout (int): Seconds to wait before failing.

    Returns:
        None
    """
    for cwd in get_cluster_dirs():
        infra.wait_for_odl_ready(cwd, timeout=timeout)
