"""
   Utility library for configuring NETCONF topology node to connect to network elements. This operates
   on the legacy network-topology model, with topology-id="topology-netconf".
"""

from logging import debug, info
from requests import get, patch
from sys import argv
from time import sleep, time
from uuid import uuid4


def configure_device_range(
    restconf_url,
    device_name_prefix,
    device_ipaddress,
    device_port,
    device_count,
    use_node_encapsulation,
    first_device_id=1,
):
    """Generate device_count names in format "$device_name_prefix-$i" and configure them into NETCONF topology at specified RESTCONF URL.
    For example:

       configure_device_range("http://127.0.0.1:8181/rests", "example", "127.0.0.1", 1730, 5)

    would configure devices "example-0" through "example-4" to connect to 127.0.0.0:1730.

       configure_device_range("http://127.0.0.1:8181/rests", "example", "127.0.0.1", 1720, 5, 5)

    would configure devices "example-5" through "example-9" to connect to 127.0.0.0:1720.

    This method assumes RFC8040 with RFC7952 encoding and support for RFC8072 (YANG patch). Payload it generates looks roughly like this:
    {
       "ietf-yang-patch:yang-patch" : {
          "patch-id" : "test"
          "edit" : [
             {
                "edit-id" : "test-edit",
                "operation" : "replace",
                "target" : "/node=test-node",
                "value" : {
                   "node" : [
                      {
                         "node-id" : "test-node"
                         "netconf-node-topology:host" : "127.0.0.1",
                         "netconf-node-topology:port" : 17830,
                         "netconf-node-topology:username" : "admin",
                         "netconf-node-topology:password" : "topsecret",
                         "netconf-node-topology:keepalive-delay" : 0,
                      }
                   ]
                }
             }
          ],
       }
    }
    """

    info(
        "Configure %s devices starting from %s (at %s:%s)",
        device_count,
        first_device_id,
        device_ipaddress,
        device_port,
    )

    device_names = []
    edits = []

    for i in range(first_device_id, first_device_id + device_count):
        name = "{}-{}".format(device_name_prefix, i)
        device_names.append(name)
        if use_node_encapsulation:
            edits.append(get_encapsulated_payload(name, device_ipaddress, device_port))
        else:
            edits.append(get_legacy_payload(name, device_ipaddress, device_port))

    data = """
    {
      "ietf-yang-patch:yang-patch" : {
        "patch-id" : "csit-%s",
        "edit" : [
    """ % str(
        uuid4()
    )

    # TODO: I bet there is a fancier way to write this
    it = iter(edits)
    cur = next(it)
    while True:
        data = data + cur
        nxt = next(it, None)
        if nxt is None:
            break
        data += ", "
        cur = nxt

    data += """]
      }
    }"""

    resp = patch(
        url=restconf_url
        + """/data/network-topology:network-topology/topology=topology-netconf""",
        headers={
            "Content-Type": "application/yang-patch+json",
            "Accept": "application/yang-data+json",
            "User-Agent": "csit agent",
        },
        data=data,
        # FIXME: do not hard-code credentials here
        auth=("admin", "admin"),
    )

    resp.raise_for_status()
    status = resp.json()
    # FIXME: validate response
    #  {
    #    "ietf-yang-patch:yang-patch-status" : {
    #      "patch-id" : "add-songs-patch-2",
    #      "ok" : [null]
    #    }
    #  }

    #  {
    #    "ietf-yang-patch:yang-patch-status" : {
    #      "patch-id" : "add-songs-patch",
    #      "edit-status" : {
    #        "edit" : [
    #          {
    #            "edit-id" : "edit1",
    #            "errors" : {
    #              "error" : [
    #                {
    #                  "error-type": "application",
    #                  "error-tag": "data-exists",
    #                  "error-path": "/example-jukebox:jukebox/library\
    #                     /artist[name='Foo Fighters']\
    #                     /album[name='Wasting Light']\
    #                     /song[name='Bridge Burning']",
    #                  "error-message":
    #                    "Data already exists; cannot be created"
    #                }
    #              ]
    #            }
    #          }
    #        ]
    #      }
    #    }
    #  }

    return device_names


def get_legacy_payload(name, device_ipaddress, device_port):
    return """
        {
          "edit-id" : "node-%s",
          "operation" : "replace",
          "target": "/node=%s",
          "value" : {
            "node" : [
              {
                "node-id" : "%s",
                "netconf-node-topology:host" : "%s",
                "netconf-node-topology:port" : %s,
                "netconf-node-topology:login-password-unencrypted": {
                    "username": "admin",
                    "password": "topsecret"
                },
                "netconf-node-topology:tcp-only" : false,
                "netconf-node-topology:keepalive-delay" : 0
              }
            ]
          }
        }
    """ % (
        name,
        name,
        name,
        device_ipaddress,
        device_port,
    )


def get_encapsulated_payload(name, device_ipaddress, device_port):
    return """
        {
          "edit-id" : "node-%s",
          "operation" : "replace",
          "target": "/node=%s",
          "value" : {
            "node" : [
              {
                "node-id" : "%s",
                "netconf-node-topology:netconf-node":{
                    "host" : "%s",
                    "port" : %s,
                    "login-password-unencrypted": {
                        "username": "admin",
                        "password": "topsecret"
                    },
                    "tcp-only" : false,
                    "keepalive-delay" : 0
                  }
              }
            ]
          }
        }
    """ % (
        name,
        name,
        name,
        device_ipaddress,
        device_port,
    )


def await_devices_connected(
    restconf_url, device_names, deadline_seconds, use_node_encapsulation
):
    """Await all specified devices to become connected in NETCONF topology at specified RESTCONF URL."""

    info("Awaiting connection of %s", device_names)
    deadline = time() + deadline_seconds
    names = set(device_names)
    connected = set()

    while time() < deadline:
        resp = get(
            url=restconf_url
            + """/data/network-topology:network-topology/topology=topology-netconf?content=nonconfig""",
            headers={"Accept": "application/yang-data+json"},
            # FIXME: do not hard-code credentials here
            auth=("admin", "admin"),
        )

        # FIXME: also check for 409 might be okay?
        resp.raise_for_status()

        if "node" not in resp.json()["network-topology:topology"][0]:
            sleep(1)
            continue

        # Check all reported nodes
        for node in resp.json()["network-topology:topology"][0]["node"]:
            name = node["node-id"]
            if use_node_encapsulation:
                status = node["netconf-node-topology:netconf-node"]["connection-status"]
            else:
                status = node["netconf-node-topology:connection-status"]
            debug("Evaluating %s status %s", name, status)

            if name in names:
                if status == "connected":
                    if name not in connected:
                        debug("Device %s connected", name)
                        connected.add(name)
                elif name in connected:
                    # also remove from connected in case we switched from
                    # connected on a device from previous iteration
                    connected.remove(name)

        if len(connected) == len(names):
            return

        sleep(1)

    raise Exception("Timed out waiting for %s to connect" % names.difference(connected))


def main(args):
    # FIXME: add proper option parsing
    if args[0] == "configure":
        names = configure_device_range(
            restconf_url="http://127.0.0.1:8181/rests",
            device_name_prefix="example",
            device_ipaddress="127.0.0.1",
            device_port=17830,
            device_count=int(args[1]),
            use_node_encapsulation=True,
        )
        print(names)
    elif args[0] == "await":
        await_devices_connected(
            restconf_url="http://127.0.0.1:8181/rests",
            deadline_seconds=5,
            device_names=args[1:],
            use_node_encapsulation=True,
        )
    else:
        raise Exception("Unhandled argument %s" % args[0])


if __name__ == "__main__":
    # i.e. main does not depend on name of the binary
    main(argv[1:])
