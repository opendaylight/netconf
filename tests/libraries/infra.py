#
# Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

from collections.abc import Generator
from contextlib import contextmanager
import io
import logging
import os
import signal
import subprocess

import allure
import psutil

from libraries import utils
from libraries.KarafShell import KarafShell
from libraries.variables import variables

CONTROLLER_MAX_MEM = variables.CONTROLLER_MAX_MEM
karaf_shell_instances = dict()

log = logging.getLogger(__name__)


def shell(
    command: str | list | tuple,
    joiner="; ",
    cwd: str | None = None,
    use_shell=True,
    run_in_background: bool = False,
    timeout: int = None,
    check_rc=False,
):
    """Runs single or multiple shell commands.

    Multiple shell command are concatenated together by using joiner.
    It provides multiple options on how to run the command.

    Args:
        command (str | list | tuple): Shell command(s) to be run.
        joiner (str): Joiner for concatenating multiple commands.
        cwd (str): Current working directory from where the command
            needs to be executed.
        run_in_backgroud (bool): If the command should be started as background
            process without tty.
        timeotu (int): Timeout in seconds for the foreground command.

    Returns:
        tuple[int, str] | subprocess.Popen :
            For foreground process it returns final return code and stdout,
            for backgroud process it returns process handler.
    """
    exec_command = command
    if isinstance(command, (list, tuple)):
        exec_command = joiner.join(command)

    try:
        log.info(exec_command)
        if run_in_background:
            if use_shell:
                process = subprocess.Popen(
                    f"exec {exec_command}",
                    shell=True,
                    text=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    stdin=subprocess.DEVNULL,
                    bufsize=1,
                    cwd=cwd,
                )
            else:
                process = subprocess.Popen(
                    exec_command.split(" "), shell=False, text=True, cwd=cwd
                )
            return process
        else:
            result = subprocess.run(
                exec_command,
                shell=use_shell,
                check=True,
                capture_output=True,
                text=True,
                timeout=timeout,
                cwd=cwd,
            )
            log.debug(f"{result.returncode:3d} |--| {result.stdout}")
            if check_rc and result.returncode != 0:
                return AssertionError(f"Expected command {exec_command} to pass")
            return result.returncode, result.stdout
    except subprocess.CalledProcessError as e:
        std_error = e.stderr.strip()
        log.error(
            f"ERROR while command execution '{exec_command}'"
            f"{':\n' + std_error if std_error else ''}"
        )
        return e.returncode, e.stdout
    except FileNotFoundError:
        log.error(f"ERROR command not found: {exec_command}")
        return None, None


def retry_shell_command(
    retry_count: int, interval: int, *args, **kwargs
) -> tuple[int, str]:
    """Repeatedly runs a shell command until the return code is 0.

    Args:
        retry_count (int): Maximum number of retries
        interval (int): Number of seconds to wait until next retry.
        *args: shell positional argments
        **kwargs: shell keyword arguments

    Returns:
        tuple[int, str]: Return code and standart output of successful run
            of the command.
    """
    validator = lambda result: result[0] == 0
    rc, output = utils.wait_until_function_returns_value_with_custom_value_validator(
        retry_count, interval, validator, shell, *args, **kwargs
    )
    return rc, output


def count_port_occurrences(port: int, state: str, name: str) -> int:
    """Counts number of occurrences of specific port types.

    Args:
        port (str): Port number.
        state (str): Port state.
        name (str): Name of the program using the port.

    Returns:
        int: Number of port occurrences.
    """
    rc, stdout = shell(
        f'ss -punta 2> /dev/null | grep -E "{state} .+:{port} .+{name}" | wc -l'
    )
    assert (
        int(stdout) == 1
    ), f"Failed to check number of occurrences for {port=} {state=} {name=}"
    return int(stdout)


def _start_karaf(cwd: str):
    """Starts the Karaf process in `cwd`.

    Args:
        cwd (str): Distribution directory to start.

    Returns:
        None
    """
    shell(f"JAVA_OPTS=-Xmx{CONTROLLER_MAX_MEM} ./bin/start", cwd=cwd)


def start_odl_with_features(features: tuple[str], cwd: str = "opendaylight"):
    """Starts ODL with installed provided features.

    Args:
        features (tuple[str]): Features to be installed in ODL.
        cwd (str): Distribution directory to start, relative to the working
            directory. Defaults to the single-node distribution, but cluster
            members each get their own copy.

    Returns:
        None
    """

    # set config with the required features
    shell(
        f"sed -ie 's/\(featuresBoot=\|featuresBoot =\)/featuresBoot = "
        f"{",".join(features)},/g' etc/org.apache.karaf.features.cfg",
        cwd=cwd,
    )

    shell(
        "sed -ie 's/memory-mapped = true/memory-mapped = false/g' "
        "system/org/opendaylight/controller/sal-clustering-config/*/"
        "sal-clustering-config-*-factorypekkoconf.xml",
        cwd=cwd,
    )

    _start_karaf(cwd)


def count_system_ready(cwd: str = "opendaylight") -> int:
    """Counts the "System ready" lines logged so far in distribution `cwd`.

    Reads every karaf.log* file, not just the current one, so a log
    rollover moving earlier lines into karaf.log.1 doesn't lower the count.

    Args:
        cwd (str): Distribution directory whose logs to read.

    Returns:
        int: Number of "System ready" lines found.
    """
    _, out = shell(
        "cat data/log/karaf.log* 2>/dev/null"
        " | grep -c 'org.opendaylight.infrautils.*System ready' || true",
        cwd=cwd,
    )
    return int(out.strip() or 0)


def wait_for_odl_ready(
    cwd: str = "opendaylight", timeout: int = 60, previous_count: int = 0
):
    """Blocks until ODL logs "System ready" in the given distribution more
    times than `previous_count`.

    The default of 0 waits for the first one. After a restart, the lines
    from before it are still in the log, so pass the count_system_ready
    value taken before the restart to wait for a new one instead.

    Args:
        cwd (str): Distribution directory whose log to poll.
        timeout (int): Seconds to wait before failing.
        previous_count (int): Number of "System ready" lines to ignore.

    Returns:
        None
    """

    def _verify_new_system_ready():
        assert (
            count_system_ready(cwd) > previous_count
        ), f"ODL in {cwd} hasn't logged a new 'System ready' yet"

    interval = 5
    utils.wait_until_function_pass(
        timeout // interval, interval, _verify_new_system_ready
    )


def stop_all_karaf_instances():
    """Kills every running Karaf main process.

    Matches on the process command line, so it stops all ODL instances at
    once regardless of how many are running (single node or cluster members).
    Also discards every cached Karaf shell connection, since all of them
    would otherwise be left pointing at a now-dead process.

    Args:
        None

    Returns:
        None
    """
    shell("kill $(pgrep -f org.apache.karaf.main.[M]ain | grep -v ^$$\\$)")
    for shell_instance in karaf_shell_instances.values():
        shell_instance.close()
    karaf_shell_instances.clear()


def _find_karaf_processes(cwd: str) -> list[psutil.Process]:
    """Finds the Karaf main process(es) launched from distribution `cwd`.

    Karaf's start script passes its own distribution directory, resolved to
    an absolute path, as -Dkaraf.base, so an exact match on that argument
    identifies only this instance - unlike a substring match, it can't
    confuse "opendaylight" with "opendaylight-member-1".

    Args:
        cwd (str): Distribution directory the Karaf was started from.

    Returns:
        list[psutil.Process]: Matching processes, possibly empty.
    """
    base_arg = f"-Dkaraf.base={os.path.realpath(cwd)}"
    matches = []
    for process in psutil.process_iter(["cmdline"]):
        cmdline = process.info["cmdline"] or []
        if "org.apache.karaf.main.Main" in cmdline and base_arg in cmdline:
            matches.append(process)
    return matches


def stop_karaf(cwd: str = "opendaylight", host: str = "127.0.0.1", port: int = 8101):
    """Stops only the Karaf process launched from `cwd` and waits until it's
    gone, leaving any other instance on the host (e.g. other cluster
    members) running.

    Also discards the cached Karaf shell connection to this instance, since
    it would otherwise be left pointing at a now-dead process.

    Args:
        cwd (str): Distribution directory to stop.
        host (str): Address of this instance's Karaf SSH console.
        port (int): Karaf SSH port of this instance.

    Returns:
        None
    """
    for process in _find_karaf_processes(cwd):
        stop_process_by_pid(process.pid, timeout=60)
    shell_instance = karaf_shell_instances.pop((host, port), None)
    if shell_instance is not None:
        shell_instance.close()


def _verify_karaf_shell_reachable(host: str, port: int):
    """Asserts the Karaf SSH console accepts a connection.

    Uses its own throwaway connection rather than the shared cache: this is
    a check, not a place to establish the connection other callers reuse.

    Args:
        host (str): Node to probe.
        port (int): Karaf SSH port.

    Returns:
        None
    """
    instance = KarafShell(host=host, port=port)
    try:
        instance.connect()
    finally:
        instance.close()


def wait_for_karaf_shell_ready(host: str = "127.0.0.1", port: int = 8101):
    """Blocks until the Karaf SSH console is reachable.

    Args:
        host (str): Node to probe.
        port (int): Karaf SSH port.

    Returns:
        None
    """
    utils.wait_until_function_pass(30, 2, _verify_karaf_shell_reachable, host, port)


def restart_karaf(
    cwd: str = "opendaylight",
    host: str = "127.0.0.1",
    port: int = 8101,
    timeout: int = 300,
):
    """Restarts the Karaf process running in `cwd`, and waits until ODL
    logs a new "System ready" and its SSH console is reachable again. Any
    other Karaf on the host (e.g. other cluster members) is left running.

    Reuses the directory's existing configuration as-is - unlike
    start_odl_with_features, this never touches featuresBoot, since it's
    for restarting an already-configured instance, not a first-time setup.

    "System ready" is the same signal the session setup waits for, so
    after this returns every installed feature is as ready as it was at
    the start of the session. The "System ready" lines from before the
    restart are still in the log, so this waits for their count to grow
    rather than for any line to be present.

    Args:
        cwd (str): Distribution directory to restart.
        host (str): Node whose Karaf SSH console to wait for.
        port (int): Karaf SSH port of this instance.
        timeout (int): Seconds to wait for the new "System ready".

    Returns:
        None
    """
    previous_count = count_system_ready(cwd)
    stop_karaf(cwd, host=host, port=port)
    _start_karaf(cwd)
    wait_for_odl_ready(cwd, timeout=timeout, previous_count=previous_count)
    wait_for_karaf_shell_ready(host=host, port=port)


def execute_karaf_command(
    command: str, host: str = "127.0.0.1", port: int = 8101
) -> tuple[str, str]:
    """Executed specific command using ODL karaf CLI console

    It usses ssh connection to connect to karaf CLI.

    Args:
        command (str): Command to be executed.
        host (str): Address of the karaf instance to connect to. Defaults to
            the single-node address; cluster members each get their own.
        port (int): Karaf SSH port of the instance to connect to.

    Returns:
        tuple[str, str]: Stdout from karaf CLI, stderr from karaf CLI.
    """
    log.info(f"Executing command '{command}' on karaf console {host}:{port}.")

    if (host, port) not in karaf_shell_instances:
        karaf_shell_instances[(host, port)] = KarafShell(host=host, port=port)
    shell_instance = karaf_shell_instances[(host, port)]

    try:
        stdout = shell_instance.execute(command)
        log.info(f"Command Output:\n{stdout}")

        return stdout, ""

    except Exception as e:
        log.error(f"Failed to execute karaf command: {e}")
        return "", str(e)


def log_message_to_karaf(message: str, host: str = "127.0.0.1"):
    """Log specific mesage to ODL karaf

    It usses ssh connection to connect to karaf CLI.

    Args:
        message (str): Message to be logged.
        host (str): Address of the karaf instance to log to. Defaults to
            the single-node address; cluster members each get their own.

    Returns:
        None
    """
    execute_karaf_command(f"log:log 'ROBOT MESSAGE: {message}'", host=host)


@contextmanager
def log_step(title: str, log_format: str) -> Generator[any, None, None]:
    """Execute an Allure step with its own separately-captured logging.

    Standalone counterpart to the allure_step_with_separate_logging fixture,
    for use from a fixture whose own scope (e.g. class) is broader than that
    fixture's function scope - pytest does not allow a broader-scoped
    fixture to depend on a narrower-scoped one.

    Args:
        title (str): Step title.
        log_format (str): Log line format, as returned by
            request.config.getini("log_format").

    Returns:
        Generator[any, None, None]: context manager for allure step
    """
    log_capture_string = io.StringIO()
    handler = logging.StreamHandler(log_capture_string)
    formatter = logging.Formatter(log_format)
    handler.setFormatter(formatter)

    root_logger = logging.getLogger()
    root_logger.addHandler(handler)

    try:
        with allure.step(title) as allure_step:
            log_message_to_karaf(f"Starting step: {title}")
            yield allure_step
    finally:
        log_message_to_karaf(f"End of step: {title}")
        root_logger.removeHandler(handler)
        log_contents = log_capture_string.getvalue()
        if log_contents:
            allure.attach(
                log_contents,
                name=f"Logs for '{title}'",
                attachment_type=allure.attachment_type.TEXT,
            )


def is_karaf_feature_installed(feature_name: str, host: str = "127.0.0.1") -> bool:
    """Check if the given feature is found in the output of 'feature:list -i'.

    Args:
        feature_name (str): Exact name of the Karaf feature to look up.
        host (str): Address of the karaf instance to check. Defaults to
            the single-node address; cluster members each get their own.

    Returns:
        bool: True if the feature is installed, False otherwise.
    """
    output, _ = execute_karaf_command(
        f"feature:list -i | grep {feature_name}", host=host
    )
    return any(line.split(" ")[0] == feature_name for line in output.splitlines())


def is_process_still_running(pid: int):
    """Check if provided process did not finish yet.

    Args:
        process (subprocess.Popen): Process handler.

    Returns:
        None
    """
    try:
        process = psutil.Process(pid)
    except psutil.NoSuchProcess:
        return False
    return process.is_running() and process.status() != psutil.STATUS_ZOMBIE


def get_file_content(path: str) -> str:
    """Returns text file content.

    Args:
        path (str): Text file path.

    Returns:
        str: Text file content.
    """
    with open(path, "r", encoding="utf-8") as file:
        content = file.read()

    return content


def save_text_to_a_file(file_path: str, content: str):
    """Writes text content to a file.

    Args:
        file_path (str): The destination file path.
        content (str): The text content to write.

    Returns:
        None
    """
    with open(file_path, "w", encoding="utf-8") as file:
        file.write(content)


def copy_file(
    src_dir: str,
    src_file_name: str,
    dst_dir: str,
    target_file_name: str | None = None,
):
    """Copy file from one location to another.

    By providing target_file_name parameter, file would be renamed.

     Args:
        src_dir (str): Source file directory in which it is located.
        src_file_name (str): Name of the file to be copied.
        dst_dir (str): Destination directory where the file should be copied.
        target_file_name (str): Optional target file name, set if the copied
            file needs to be stored under different name in the target directory.
            By default it keeps the file name.

    Returns:
        None
    """
    if target_file_name is None:
        target_file_name = src_file_name
    shell(f"cp {src_dir}/{src_file_name} {dst_dir}/{target_file_name}")


def copy_dir(
    src_dir: str,
    dst_dir: str,
):
    """Copy directory from one location to another.

     Args:
        src_dir (str): Source directory to be copied.
        dst_dir (str): Destination directory where the directory should be copied.

    Returns:
        None
    """
    shell(f"cp -r {src_dir} {dst_dir}")


def stop_process_by_pid(pid: int, gracefully: bool = True, timeout: int | None = 5):
    """Stops process by sending signal and verifies it is not running.

    Args:
        pid (int): The operating system PID of the target process.
        gracefully (bool): Determines which signal should be sent,
            for gracefully it sends SIGTERM, otherwise SIGKILL
        timeout (int | None): Seconds to wait for termination. None skips verification.

    Returns:
        None
    """
    log.info(f"Stopping process with PID {pid}")
    signal_to_be_sent = signal.SIGTERM if gracefully else signal.SIGKILL
    log.info(f"Sending signal {signal_to_be_sent} to process with PID {pid}")
    process = psutil.Process(pid)
    process.send_signal(signal_to_be_sent)

    if timeout is not None:
        # check if it is still running
        try:
            utils.wait_until_function_returns_value(
                timeout, 1, False, is_process_still_running, process.pid
            )
        except AssertionError as e:
            raise AssertionError(
                (
                    f"Was not able to stop process with PID {process.pid}, "
                    f"it is still running."
                )
            ) from e
