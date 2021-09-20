"""
    Utility library for retrieving entity related data from ODL.
"""

from logging import info
from requests import post
from sys import argv


def get_entities(restconf_url):
    resp = post(
        url=restconf_url + """/operations/odl-entity-owners:get-entities""",
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "csit agent",
        },
        auth=("admin", "admin"),
    )

    info(
        "Response %s ",
        resp,
    )

    return resp.json()


def get_entity(restconf_url, type, name):
    """Calls the get-entity rpc on the controller and returns the result in a
    dictionary that contains the parsed response in two keys:
    "candidates" and "owner"
    """

    data = """
    {
        "odl-entity-owners:input" : {
            "type": "%s",
            "name": "%s"
        }
    }
    """ % (
        type,
        name,
    )

    info("Data %s", data)

    resp = post(
        url=restconf_url + """/operations/odl-entity-owners:get-entity""",
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "csit agent",
        },
        data=data,
        auth=("admin", "admin"),
    )

    info(
        "Entity json %s",
        resp.json(),
    )

    result = {
        "candidates": resp.json()["odl-entity-owners:output"]["candidate-nodes"],
        "owner": resp.json()["odl-entity-owners:output"]["owner-node"],
    }

    return result


def get_entity_owner(restconf_url, type, name):
    data = """
    {
        "odl-entity-owners:input" : {
            "type": "%s",
            "name": "%s"
        }
    }
    """ % (
        type,
        name,
    )

    info("Data %s", data)

    resp = post(
        url=restconf_url + """/operations/odl-entity-owners:get-entity-owner""",
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "csit agent",
        },
        data=data,
        auth=("admin", "admin"),
    )

    info(
        "Response %s ",
        resp,
    )

    return resp.json()["odl-entity-owners:output"]["owner-node"]


def main(args):
    if args[0] == "get-entities":
        json = get_entities(
            restconf_url="http://127.0.0.1:8181/rests",
        )
        print(json)
    elif args[0] == "get-entity":
        json = get_entity(
            restconf_url="http://127.0.0.1:8181/rests",
            type=args[1],
            name=args[2],
        )
        print(json)
    elif args[0] == "get-entity-owner":
        json = get_entity_owner(
            restconf_url="http://127.0.0.1:8181/rests",
            type=args[1],
            name=args[2],
        )
        print(json)
    else:
        raise Exception("Unhandled argument %s" % args[0])


if __name__ == "__main__":
    # i.e. main does not depend on name of the binary
    main(argv[1:])
