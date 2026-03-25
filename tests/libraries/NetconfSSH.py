#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

import logging
import time

from libraries import ssh_utils

log = logging.getLogger(__name__)


class NetconfSSH:
    """
    SSH client for NETCONF sessions.

    Connects to NETCONF server and handles xml message exchange.
    """

    def __init__(self, host="127.0.0.1", port=2830, user="admin", password="admin"):
        self.host = host
        self.port = port
        self.user = user
        self.password = password
        self.client = None
        self.channel = None

    def connect(self):
        """Connects to the NETCONF server over SSH and retrieves the hello message.

        Returns:
            str: The initial <hello> XML message received from the NETCONF server.
        """
        log.info(f"Opening Netconf SSH to {self.host}:{self.port}...")
        self.client = ssh_utils.create_ssh_client(
            self.host, self.port, self.user, self.password
        )

        transport = self.client.get_transport()
        self.channel = transport.open_session()
        self.channel.invoke_subsystem("netconf")
        server_hello = self.read_until_prompt()
        log.info("Netconf successfully connected.")
        log.debug(f"Server <hello> message received:\n{server_hello}")
        return server_hello

    def write(self, text: str):
        """Writes raw text directly to the SSH channel.

        Args:
            text (str): The raw string data to be sent.

        Returns:
            None
        """
        self.channel.send(text)
        log.debug(f"NetconfSSH sent:\n{text}")

    def send_hello(self, xml_message: str):
        """Sends the client <hello> message to the server.

        Automatically appends the ]]>]]> framing delimiter if missing.

        Args:
            xml_message (str): The XML <hello> message to be sent.

        Returns:
            None
        """
        if not xml_message.endswith("]]>]]>"):
            xml_message += "]]>]]>"
        self.write(xml_message)
        log.debug("Client <hello> sent successfully.")

    def send_rpc(self, xml_message: str) -> str:
        """Sends an XML <rpc> request and waits for the <rpc-reply>.

        Automatically appends the ]]>]]> framing delimiter if missing.

        Args:
            xml_message (str): The XML <rpc> payload to be sent.

        Returns:
            str: The raw XML <rpc-reply> received from the server.
        """
        if not xml_message.endswith("]]>]]>"):
            xml_message += "]]>]]>"

        self.write(xml_message)
        output = self.read_until_prompt()

        return output

    def read_until_prompt(self, timeout=30) -> str:
        """Reads from the channel until the NETCONF ]]>]]> delimiter is received.

        Args:
            timeout (int): Timeout in seconds.

        Returns:
            str: The raw XML buffer received from the server, including the delimiter.
        """
        buffer = ""
        start_time = time.time()
        while True:
            if time.time() - start_time > timeout:
                raise TimeoutError(
                    f"Timed out waiting for Netconf ]]>]]> delimiter. Buffer:\n{buffer}"
                )

            if self.channel.recv_ready():
                buffer += self.channel.recv(4096).decode("utf-8")
                if "]]>]]>" in buffer:
                    log.debug(f"NetconfSSH received:\n{buffer}")
                    return buffer
            time.sleep(0.05)
