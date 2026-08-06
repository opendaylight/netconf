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

from collections.abc import Callable
import logging

from libraries import infra
from libraries import netconf
from libraries import templated_requests
from libraries import utils
from libraries.variables import variables

CLUSTER_MEMBER_IPS = variables.CLUSTER_MEMBER_IPS
ODL_IP = variables.ODL_IP
RESTCONF_ROOT = variables.RESTCONF_ROOT
CONTROLLER_MAX_MEM = variables.CONTROLLER_MAX_MEM

# How long a data operation or an ownership check keeps being retried
# (retry count x interval seconds) while the cluster settles after a mount or
# a member restart. Generous, because a member rejoining the cluster has to
# re-elect entity owners and re-establish the device's mount points before
# writes routed through any member are guaranteed to reach the device.
DATA_OPERATION_RETRY_COUNT = 120
DATA_OPERATION_RETRY_INTERVAL = 1

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


def get_member_ip(member: int) -> str:
    """Address of cluster member ``member``.

    Members are numbered from 1, matching pekko's ``member-N`` node names and
    the 1-based index passed to bin/configure_cluster.sh during setup.

    Args:
        member (int): 1-based member number.

    Returns:
        str: The member's address from CLUSTER_MEMBER_IPS.
    """
    return CLUSTER_MEMBER_IPS[member - 1]


def _get_member_process_pattern(member: int) -> str:
    """pgrep/pkill pattern matching only member ``member``'s Karaf process.

    Every member's distribution directory name (opendaylight-member-N) appears
    verbatim in its Java command line via -Dkaraf.base, so it uniquely
    identifies that member's process. The leading character is wrapped in a
    class ([o]) so the pattern never matches the shell that runs pgrep/pkill
    itself -- the classic self-match avoidance also used by
    infra.stop_all_karaf_instances.

    Args:
        member (int): 1-based member number.

    Returns:
        str: Regex suitable for `pgrep -f` / `pkill -f`.
    """
    return f"[o]pendaylight-member-{member}"


def _verify_member_stopped(member: int):
    """Asserts member ``member`` has no running Karaf process.

    Args:
        member (int): 1-based member number.

    Returns:
        None
    """
    # `|| true` keeps the expected no-match case (pgrep exit 1, which is what we
    # want here) from being logged as a shell error; the assertion is decided by
    # stdout being empty, not by the exit code.
    _, out = infra.shell(f"pgrep -f '{_get_member_process_pattern(member)}' || true")
    assert not out.strip(), f"member-{member} Karaf process is still running"


def kill_member(member: int):
    """Forcibly kills a single cluster member and waits for it to be gone.

    Sends SIGKILL to the member's Karaf process (simulating an abrupt node
    failure) then polls until no matching process remains.

    Args:
        member (int): 1-based member number to kill.

    Returns:
        None
    """
    log.info(f"Killing cluster member-{member} ({get_member_ip(member)})")
    infra.shell(f"pkill -9 -f '{_get_member_process_pattern(member)}'")
    utils.wait_until_function_pass(12, 5, _verify_member_stopped, member)


def _verify_restconf_available(member: int):
    """Asserts RESTCONF answers on member ``member``.

    Args:
        member (int): 1-based member number.

    Returns:
        None
    """
    templated_requests.get_from_uri(
        f"{RESTCONF_ROOT}/data/network-topology:network-topology?content=config",
        host=get_member_ip(member),
        http_timeout=10,
    )


def start_member(member: int, timeout: int = 300):
    """Restarts a single cluster member and waits until RESTCONF is available.

    Reuses the member's existing distribution directory (already configured and
    wired into the cluster by setup_cluster), so unlike start_odl_with_features
    it does not re-edit featuresBoot. Readiness is decided by polling RESTCONF
    rather than grepping the log for "System ready", because the pre-kill
    "System ready" line survives the restart and would match immediately.

    Args:
        member (int): 1-based member number to start.
        timeout (int): Seconds to wait for RESTCONF before failing.

    Returns:
        None
    """
    member_dir = get_member_dir(member - 1)
    log.info(
        f"Starting cluster member-{member} ({get_member_ip(member)}) from {member_dir}"
    )
    infra.shell(f"JAVA_OPTS=-Xmx{CONTROLLER_MAX_MEM} ./bin/start", cwd=member_dir)
    interval = 5
    utils.wait_until_function_pass(
        timeout // interval, interval, _verify_restconf_available, member
    )


def _parse_member_number(node_name: str) -> int:
    """Parses a pekko node name like "member-2" into its 1-based number.

    Args:
        node_name (str): Node name in "member-N" form.

    Returns:
        int: The member number N.
    """
    return int(node_name.replace("member-", ""))


# Entity type under which mdsal's cluster singleton service elects the owner of
# a netconf device. The device's entity name is a long DataObjectIdentifier
# blob embedding the topology node id (its exact form is codegen- and
# version-specific), so the entity is located by the node id substring rather
# than by reconstructing the name.
_NETCONF_ELECTION_ENTITY_TYPE = "org.opendaylight.mdsal.ServiceEntityType"


def _get_entities(host: str) -> list[dict]:
    """Returns every entity-owner entity as reported by ``host``.

    Args:
        host (str): Cluster member to send the RPC to.

    Returns:
        list[dict]: One dict per entity, each carrying "type", "name",
            "owner-node" and "candidate-nodes".
    """
    uri = f"{RESTCONF_ROOT}/operations/odl-entity-owners:get-entities"
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    response = templated_requests.post_to_uri(uri, headers=headers, data="", host=host)
    return response.json()["odl-entity-owners:output"].get("entities", [])


def get_device_entity_owner_and_followers(
    device_name: str, host: str = ODL_IP
) -> tuple[int, list[int]]:
    """Returns the owner and followers of the entity representing a netconf device.

    Calls the odl-entity-owners:get-entities RPC on ``host`` and locates the
    ClusterSingletonService election entity for ``device_name`` -- the one
    whose type is the mdsal ServiceEntityType and whose name embeds the
    device's topology node id. The returned "member-N" node names are mapped to
    member numbers; followers are all candidates other than the owner, sorted
    ascending.

    Args:
        device_name (str): Name of the mounted netconf device.
        host (str): Cluster member to send the RPC to.

    Returns:
        tuple[int, list[int]]: (owner member number, follower member numbers).
    """
    matches = [
        entity
        for entity in _get_entities(host)
        if entity.get("type") == _NETCONF_ELECTION_ENTITY_TYPE
        and f"value={device_name}}}" in entity.get("name", "")
    ]
    assert len(matches) == 1, (
        f"expected exactly one {_NETCONF_ELECTION_ENTITY_TYPE} entity for device "
        f"{device_name}, found {len(matches)}"
    )
    entity = matches[0]

    owner = _parse_member_number(entity["owner-node"])
    candidates = sorted(
        _parse_member_number(node) for node in entity["candidate-nodes"]
    )
    followers = [candidate for candidate in candidates if candidate != owner]
    return owner, followers


def wait_device_ownership_settled(
    device_name: str,
    host: str = ODL_IP,
    retry_count: int = DATA_OPERATION_RETRY_COUNT,
    interval: int = DATA_OPERATION_RETRY_INTERVAL,
):
    """Waits until every cluster member is a candidate for the device entity.

    Being connected on a member is not enough: right after a fresh mount or a
    member restart the entity ownership and the device's mount points are still
    churning, and data operations issued during that window can be silently
    lost. Owner + all remaining members as followers means every member has
    registered as a candidate and the churn has settled.

    Args:
        device_name (str): Name of the mounted netconf device.
        host (str): Cluster member to query.
        retry_count (int): Maximum number of checks before failing.
        interval (int): Seconds to wait between checks.

    Returns:
        None
    """
    expected_followers = len(CLUSTER_MEMBER_IPS) - 1

    def ownership_settled():
        """Assert the device entity has an owner and all other members follow."""
        _, followers = get_device_entity_owner_and_followers(device_name, host=host)
        assert (
            len(followers) == expected_followers
        ), f"ownership not settled, followers={followers}"

    utils.wait_until_function_pass(retry_count, interval, ownership_settled)


def _wait_until_device_data_applied(
    device_name: str,
    expected: str,
    host: str,
    data_operation: Callable[[], None],
    retry_count: int,
    interval: int,
):
    """Retries a data operation until the device really reflects its result.

    A write routed through a cluster member can fail or be lost in two ways
    while the cluster is settling: a transient 5xx ("Can not perform commit
    when lock is released") when the owner's device session is not ready yet,
    or -- when the device's master/slave mount points are still churning, e.g.
    just after a member restart -- a 2xx response for a write that never
    reaches the device. Reading the value back inside the retry covers both:
    the operation is reissued until the device actually reflects it.

    Args:
        device_name (str): Name of the mounted netconf device.
        expected (str): Config data expected on ``host`` once the write lands.
        host (str): Cluster member the data is read back from.
        data_operation (Callable[[], None]): Sends the data operation to ODL.
        retry_count (int): Maximum number of attempts before failing.
        interval (int): Seconds to wait between attempts.

    Returns:
        None
    """

    def apply_and_verify():
        """Send the data operation, then assert the device reflects it."""
        data_operation()
        assert netconf.get_device_config_data(device_name, host=host) == expected

    utils.wait_until_function_pass(retry_count, interval, apply_and_verify)


def create_device_data(
    device_name: str,
    template_dir: str,
    expected: str,
    host: str = ODL_IP,
    retry_count: int = DATA_OPERATION_RETRY_COUNT,
    interval: int = DATA_OPERATION_RETRY_INTERVAL,
):
    """POSTs templated data to a device via one member and confirms it applied.

    The payload is only POSTed while the device does not report it yet, so a
    retry after a create that did land does not conflict with itself.

    Args:
        device_name (str): Name of the mounted netconf device.
        template_dir (str): Template folder holding the payload to POST.
        expected (str): Config data expected on ``host`` once the write lands.
        host (str): Cluster member to send the request to.
        retry_count (int): Maximum number of attempts before failing.
        interval (int): Seconds to wait between attempts.

    Returns:
        None
    """
    mapping = {"DEVICE_NAME": device_name, "RESTCONF_ROOT": RESTCONF_ROOT}

    def post_when_absent():
        """POST the payload unless the device already reports it."""
        if netconf.get_device_config_data(device_name, host=host) != expected:
            templated_requests.post_templated_request(
                template_dir, mapping, json=False, host=host
            )

    _wait_until_device_data_applied(
        device_name, expected, host, post_when_absent, retry_count, interval
    )


def modify_device_data(
    device_name: str,
    template_dir: str,
    expected: str,
    host: str = ODL_IP,
    retry_count: int = DATA_OPERATION_RETRY_COUNT,
    interval: int = DATA_OPERATION_RETRY_INTERVAL,
):
    """PUTs templated data to a device via one member and confirms it applied.

    A PUT is idempotent, so unlike create_device_data and delete_device_data
    this needs no guard against reissuing it.

    Args:
        device_name (str): Name of the mounted netconf device.
        template_dir (str): Template folder holding the payload to PUT.
        expected (str): Config data expected on ``host`` once the write lands.
        host (str): Cluster member to send the request to.
        retry_count (int): Maximum number of attempts before failing.
        interval (int): Seconds to wait between attempts.

    Returns:
        None
    """
    mapping = {"DEVICE_NAME": device_name, "RESTCONF_ROOT": RESTCONF_ROOT}

    def put_data():
        """PUT the payload to the device."""
        templated_requests.put_templated_request(
            template_dir, mapping, json=False, host=host
        )

    _wait_until_device_data_applied(
        device_name, expected, host, put_data, retry_count, interval
    )


def delete_device_data(
    device_name: str,
    template_dir: str,
    expected: str,
    host: str = ODL_IP,
    retry_count: int = DATA_OPERATION_RETRY_COUNT,
    interval: int = DATA_OPERATION_RETRY_INTERVAL,
):
    """DELETEs data of a device via one member and confirms it is gone.

    The DELETE is only sent while the device still reports something other than
    ``expected``, because reissuing it once the data is gone would answer with
    "data missing" instead of retrying the intended operation.

    Args:
        device_name (str): Name of the mounted netconf device.
        template_dir (str): Template folder whose location.uri points at the
            data to delete.
        expected (str): Config data expected on ``host`` once the data is gone,
            i.e. the empty data document.
        host (str): Cluster member to send the request to.
        retry_count (int): Maximum number of attempts before failing.
        interval (int): Seconds to wait between attempts.

    Returns:
        None
    """
    mapping = {"DEVICE_NAME": device_name, "RESTCONF_ROOT": RESTCONF_ROOT}

    def delete_when_present():
        """DELETE the data unless the device already reports it as gone."""
        if netconf.get_device_config_data(device_name, host=host) != expected:
            templated_requests.delete_templated_request(
                template_dir, mapping, host=host
            )

    _wait_until_device_data_applied(
        device_name, expected, host, delete_when_present, retry_count, interval
    )
