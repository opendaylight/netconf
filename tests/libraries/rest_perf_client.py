#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

import logging
import math

from libraries import infra
from libraries import utils
from libraries.variables import variables


ODL_IP = variables.ODL_IP
ODL_USER = variables.ODL_USER
ODL_PASSWORD = variables.ODL_PASSWORD
RESTCONF_PORT = variables.RESTCONF_PORT

log = logging.getLogger(__name__)


def invoke_restperfclient(
    edits: int,
    url: str,
    timeout: float,
    testcase: str = "",
    ip: str = ODL_IP,
    port: int = RESTCONF_PORT,
    asynchronous: bool = False,
    user: str = ODL_USER,
    password: str = ODL_PASSWORD,
) -> str:
    """Invoke RestPerfClient on the specified URL with the specified timeout.

    Assemble the RestPerfClient invocation command, invoke the assembled
    command and then check that RestPerfClient finished its run correctly.

    Args:
        edits (int): Number of edit requests to be sent.
        url (str): RESTCONF URL used for update requests.
        timeout (float): Maximum time in seconds to wait for restperfclient to finish.
        testcase (str): Name of the executed test case (used in log file name).
        ip (str): Target server IP address.
        port (int): Target server port number.
        asynchronous (bool): Flag indicating if next request should be sent before
            processing response for the previous request.
        user (str): RESTCONF username.
        password (str): RESTCONF password.

    Returns:
        str: Path to the generated RestPerfClient logs file.
    """
    log_file_name = utils.get_log_file_name("restperfclient", testcase)
    log_file = "tmp/" + log_file_name
    timeout_in_minutes = math.ceil(timeout / 60)
    command = (
        f"java -Xmx4G -jar build_tools/rest-perf-client.jar"
        f" --ip {ip}"
        f" --port {port}"
        f" --edits {edits}"
        f" --edit-content variables/netconf/RestPerfClient/request1.json"
        f" --async-requests {'true' if asynchronous else 'false'}"
        f" --auth {user} {password}"
        f" --timeout {timeout_in_minutes}"
        f" --destination {url}"
        f" 2>&1 | tee '{log_file}'"
    )
    try:
        with utils.report_known_bug_on_failure("5413"):
            log.info(f"Running restperfclient: {command}")
            # Add 2 minutes headroom over the restperfclient's own timeout
            rc, output = infra.shell(command, timeout=timeout + 120)
            log.info(f"restperfclient output: {output}")
            assert (
                "FINISHED. Execution time:" in output
            ), f"restperfclient did not finish cleanly; check {log_file}"
    finally:
        infra.shell("pkill -f 'rest-perf-client.jar' || true")
    return log_file


def grep_restperfclient_log(log_file: str, pattern: str) -> str:
    """Search for the specified string in the log file.
    
    This searches the log produced by the latest invocation of RestPerfClient.

    Args:
        log_file (str): RestPerfClient log file location.
        pattern (str): Pattern used to filter lines.

    Returns:
        str: Found lines containing provided pattern.
    """
    _, result = infra.shell(f"grep '{pattern}' '{log_file}'")
    return result.strip()
