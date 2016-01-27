"""WebSocket data receiver.

The tool receives and logs data from specified URI"""

# Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html

import argparse
import logging
from websocket import create_connection

__author__ = "Radovan Sajben"
__copyright__ = "Copyright(c) 2016, Cisco Systems, Inc."
__license__ = "Eclipse Public License v1.0"
__email__ = "rsajben@cisco.com"


def parse_arguments():
    """Use argparse to get arguments,

    Returns:
        :return: args object
    """
    parser = argparse.ArgumentParser()
    parser.add_argument("--uri", default="ws://127.0.0.1:8185/", help="URI to connect")
    parser.add_argument("--count", type=int, default=1, help="Number of messages to receive")
    parser.add_argument("--logfile", default="wsreceiver.log", help="Log file name")
    parser.add_argument("--debug", dest="loglevel", action="store_const",
                        const=logging.DEBUG, default=logging.INFO, help="Log level")
    args = parser.parse_args()
    return args


class WSReceiver(object):
    """Class which receives web socket messages."""

    def __init__(self, uri):
        """Initialise and connect to URI.

        Arguments:
            :uri: uri to connect to
        Returns:
            None
        """
        self.uri = uri
        logger.info("Connecting to: %s", self.uri)
        self.ws = create_connection(self.uri)

    def close(self):
        """Close the connection.

        Arguments:
            None
        Returns:
            None
        """
        logger.info("Disconnecting from: %s", self.uri)
        self.ws.close()

    def receive(self):
        """Receive a message.

        Arguments:
            None
        Returns:
            :return: received data
        """
        data = self.ws.recv()
        logger.info("Data received:\n%s", data)
        return data

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
    receiver = WSReceiver(args.uri)
    remains = args.count
    logger.info("Expected %d message(s)", remains)
    while remains:
        logger.info("Waiting for a message ...")
        data = receiver.receive()
        remains -= 1
        logger.info("Remaining messages to receive: %d", remains)
    logger.info("Finished ...")
    receiver.close()
