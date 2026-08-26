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
RESTPERFCLIENT_ERROR_PATTERNS = (
    "thread timed out",
    "Request failed",
    "Status code",
)

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
    log_file: str | None = None,
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
        log_file (str | None): Path to write the RestPerfClient log to. If not
            given, a unique path under tmp/ is generated.

    Returns:
        str: Path to the generated RestPerfClient logs file.
    """
    if log_file is None:
        log_file = "tmp/" + utils.get_log_file_name("restperfclient", testcase)
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


def run_restperfclient_and_collect_results(
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
    """Invoke RestPerfClient and copy its log into results/, even on failure.

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
        str: Path to the generated RestPerfClient log file, copied into results/.
    """
    log_file = "tmp/" + utils.get_log_file_name("restperfclient", testcase)
    try:
        return invoke_restperfclient(
            edits=edits,
            url=url,
            timeout=timeout,
            testcase=testcase,
            ip=ip,
            port=port,
            asynchronous=asynchronous,
            user=user,
            password=password,
            log_file=log_file,
        )
    finally:
        rc, output = infra.shell(f"cp '{log_file}' results/")
        if rc != 0:
            log.error(f"Failed to copy {log_file} into results/ (rc={rc}): {output}")


def grep_restperfclient_log(log_file: str, pattern: str) -> str:
    """Search for the specified string in the log file.

    This searches the log produced by the latest invocation of RestPerfClient.

    Args:
        log_file (str): RestPerfClient log file location.
        pattern (str): Pattern used to filter lines.

    Returns:
        str: Found lines containing provided pattern.
    """
    rc, result = infra.shell(f"grep '{pattern}' '{log_file}'")
    # rc 0 = pattern found, rc 1 = pattern not found; anything else (e.g. rc 2,
    # the log file could not be read) means the check below didn't actually run.
    assert rc in (0, 1), f"Failed to grep {log_file} for {pattern!r} (rc={rc})"
    return result.strip()


def check_restperfclient_log_has_no_errors(log_file: str) -> None:
    """Check a RestPerfClient log for known failure patterns.

    Failed requests are rejected because we don't want to test performance of
    ODL rejecting our requests.

    Args:
        log_file (str): RestPerfClient log file location.

    Returns:
        None
    """
    matches = {
        pattern: grep_restperfclient_log(log_file, pattern)
        for pattern in RESTPERFCLIENT_ERROR_PATTERNS
    }
    failures = {pattern: lines for pattern, lines in matches.items() if lines}
    assert (
        not failures
    ), f"restperfclient log contains errors ({log_file}):\n" + "\n".join(
        f"{pattern!r}: {lines}" for pattern, lines in failures.items()
    )
