#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Netconf operations against a device mounted onto a clustered ODL: locating the
# member that owns the device and applying data to it through any member. These
# live apart from cluster.py, which manages the cluster itself and stays netconf
# agnostic, and apart from netconf.py, which does not assume a cluster.
#

from collections.abc import Callable
import logging

from libraries import cluster
from libraries import netconf
from libraries import templated_requests
from libraries import utils
from libraries.variables import variables

CLUSTER_MEMBER_IPS = variables.CLUSTER_MEMBER_IPS
ODL_IP = variables.ODL_IP
RESTCONF_ROOT = variables.RESTCONF_ROOT

# How long a data operation or an ownership check keeps being retried
# (retry count x interval seconds) while the cluster settles after a mount or
# a member restart. Generous, because a member rejoining the cluster has to
# re-elect entity owners and re-establish the device's mount points before
# writes routed through any member are guaranteed to reach the device.
DATA_OPERATION_RETRY_COUNT = 120
DATA_OPERATION_RETRY_INTERVAL = 1

log = logging.getLogger(__name__)


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

    owner = cluster.parse_member_number(entity["owner-node"])
    candidates = sorted(
        cluster.parse_member_number(node) for node in entity["candidate-nodes"]
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
    expected: str = netconf.EMPTY_DEVICE_DATA,
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
        expected (str): Config data ``host`` is expected to report once the
            delete has landed. Defaults to the empty data document, which is
            what a device holding no configuration data reports; pass a
            narrower document when deleting only part of the data.
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
