"""Multithreaded utility for rapid Netconf device GET requesting.

This utility sends GET requests to ODL Netconf through Restconf to get a
bunch of configuration data from Netconf mounted devices and then checks the
results against caller provided content. The requests are sent via a
configurable number of workers. Each worker issues a bunch of blocking
restconf requests. Work is distributed in round-robin fashion. The utility
waits for the last worker to finish, or for time to run off.

The responses are checked for status (200 OK is expected) and content
(provided by user via the "--data" command line option). Results are written
to collections.Counter and printed at exit. If collections does not contain
Counter, "import Counter" is attempted.

It is advised to pin the python process to single CPU for optimal performance
as Global Interpreter Lock prevents true utilization on more CPUs (while
overhead of context switching remains).
"""

# Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html

import argparse
import collections  # For deque and Counter.
import threading
import time
import AuthStandalone


__author__ = "Vratko Polak"
__copyright__ = "Copyright(c) 2015, Cisco Systems, Inc."
__license__ = "Eclipse Public License v1.0"
__email__ = "vrpolak@cisco.com"


def str2bool(text):
    """Utility converter, based on http://stackoverflow.com/a/19227287"""
    return text.lower() in ("yes", "true", "y", "t", "1")


def parse_arguments():
    parser = argparse.ArgumentParser()

    # Netconf and Restconf related arguments.
    parser.add_argument('--odladdress', default='127.0.0.1',
                        help='IP address of ODL Restconf to be used')
    parser.add_argument('--restconfport', default='8181',
                        help='Port on which ODL Restconf to be used')
    parser.add_argument('--user', default='admin',
                        help='Username for ODL Restconf authentication')
    parser.add_argument('--password', default='admin',
                        help='Password for ODL Restconf authentication')
    parser.add_argument('--scope', default='sdn',
                        help='Scope for ODL Restconf authentication')
    parser.add_argument('--count', type=int,
                        help='Count of devices to query')
    parser.add_argument('--name',
                        help='Name of device without the ID suffix')
    parser.add_argument('--reuse', default='True', type=str2bool,
                        help='Should single requests session be re-used')

    # Work related arguments.
    parser.add_argument('--workers', default='1', type=int,
                        help='number of blocking http threads to use')
    parser.add_argument('--timeout', default='300', type=float,
                        help='timeout in seconds for all jobs to complete')
    parser.add_argument('--refresh', default='0.1', type=float,
                        help='seconds to sleep in main thread if nothing to do')

    return parser.parse_args()  # arguments are read


class TRequestWithResponse(object):

    def __init__(self, uri, kwargs):
        self.uri = uri
        self.kwargs = kwargs
        self.response_ready = threading.Event()

    def set_response(self, runtime, status, content):
        self.status = status
        self.runtime = runtime
        self.content = content
        self.response_ready.set()

    def wait_for_response(self):
        self.response_ready.wait()


def queued_send(session, queue_messages):
    """Pop from queue, Post and append result; repeat until empty."""
    while 1:
        try:
            request = queue_messages.popleft()
        except IndexError:  # nothing more to send
            break
        start = time.time()
        response = AuthStandalone.Get_Using_Session(session, request.uri, **request.kwargs)
        stop = time.time()
        status = int(response.status_code)
        content = repr(response.content)
        runtime = stop - start
        request.set_response((start, stop, runtime), status, content)


def collect_results(request_list, response_queue):
    for request in request_list:
        request.wait_for_response()
        response = (request.status, request.runtime, request.content)
        response_queue.append(response)


def watch_for_timeout(timeout, response_queue):
    time.sleep(timeout)
    response_queue.append((None, 'Time is up!'))


def run_thread(thread_target, *thread_args):
    thread = threading.Thread(target=thread_target, args=thread_args)
    thread.daemon = True
    thread.start()
    return thread


# Parse the command line arguments
args = parse_arguments()

# Construct the work for the workers.
url_start = 'config/network-topology:network-topology/'
url_start += "topology/topology-netconf/node/"
url_start += args.name + "-"
url_end = "/yang-ext:mount"
headers = {'Content-Type': 'application/xml', "Accept": "application/xml"}
kwargs = {"headers": headers}
requests = []
for device_number in range(args.count):
    device_url = url_start + str(device_number + 1) + url_end
    request = TRequestWithResponse(device_url, kwargs)
    requests.append(request)

# Organize the work into the work queues.
list_q_msg = [collections.deque() for _ in range(args.workers)]
index = 0
for request in requests:
    queue = list_q_msg[index]
    queue.append(request)
    index += 1
    if index == len(list_q_msg):
        index = 0

# Spawn the workers, giving each a queue.
threads = []
for queue_messages in list_q_msg:
    session = AuthStandalone.Init_Session(args.odladdress, args.user, args.password, args.scope, args.reuse)
    thread = run_thread(queued_send, session, queue_messages)
    threads.append(thread)

# Spawn the results collector worker
responses = collections.deque()
collector = run_thread(collect_results, requests, responses)

# Spawn the watchdog thread
watchdog = run_thread(watch_for_timeout, args.timeout, responses)

# Watch the response queue, outputting the lines
request_count = args.count
while request_count > 0:
    if len(responses) > 0:
        result = responses.popleft()
        if result[0] is None:
            print "ERROR|" + result[1] + "|"
            break
        runtime = "%5.3f|%5.3f|%5.3f" % result[1]
        print "%03d|%s|%s|" % (result[0], runtime, result[2])
        request_count -= 1
        continue
    time.sleep(args.refresh)
