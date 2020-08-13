"""Server_Sent event messages  receiver.

The tool receives and logs data from specified URI via SSE"""

import argparse
import logging
import asyncio
import aiohttp
from aiohttp_sse_client import client as sse_client


async def get_events():
    conn = aiohttp.TCPConnector()
    auth = aiohttp.BasicAuth(args.user, args.password)
    client = aiohttp.ClientSession(connector=conn, auth=auth)
    async with sse_client.EventSource(
        "http://" + args.controller + ":8181" + args.uri, session=client
    ) as event_source:
        try:
            async for event in event_source:
                logger.info(event)
        except ConnectionError:
            pass


def parse_arguments():
    """Use argparse to get arguments,

    Returns:
        :return: args object
    """
    parser = argparse.ArgumentParser()
    parser.add_argument("--controller", default="127.0.0.1", help="Controller IP")
    parser.add_argument(
        "--uri",
        default="/rests/notif/data-change-event-subscription/opendaylight-inventory:nodes/datastore=CONFIGURATION/scope=BASE",
        help="URI endpoint to connect",
    )
    parser.add_argument(
        "--count", type=int, default=1, help="Number of messages to receive"
    )
    parser.add_argument("--user", default="admin", help="Controller User")
    parser.add_argument("--password", default="admin", help="Controller Password")
    parser.add_argument("--logfile", default="ssereceiver.log", help="Log file name")
    parser.add_argument(
        "--debug",
        dest="loglevel",
        action="store_const",
        const=logging.DEBUG,
        default=logging.INFO,
        help="Log level",
    )
    args = parser.parse_args()
    return args


def main():
    loop = asyncio.get_event_loop()
    loop.run_until_complete(get_events())
    loop.run_until_complete(asyncio.sleep(0))
    loop.close()


if __name__ == "__main__":
    args = parse_arguments()
    logger = logging.getLogger("logger")
    log_formatter = logging.Formatter("%(asctime)s %(levelname)s: %(message)s")
    console_handler = logging.StreamHandler()
    file_handler = logging.FileHandler(args.logfile, mode="w")
    console_handler.setFormatter(log_formatter)
    file_handler.setFormatter(log_formatter)
    logger.addHandler(console_handler)
    logger.addHandler(file_handler)
    logger.setLevel(args.loglevel)
    logger.info("Starting to receive server-sent event messages")
    logger.info(args)
    main()
