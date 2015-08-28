__author__ = "Basheeruddin Ahmed"
__copyright__ = "Copyright(c) 2014, Cisco Systems, Inc."
__license__ = "New-style BSD"
__email__ = "syedbahm@cisco.com"


import requests
from SSHLibrary import SSHLibrary

import robot
import time
import re

global _cache


def get(url, userId='admin', password='admin'):
    """Helps in making GET REST calls"""

    headers = {}
    headers['Accept'] = 'application/xml'

    # Send the GET request
    session = _cache.switch("CLUSTERING_GET")
    resp = session.get(url, headers=headers, auth=(userId, password))
    # resp = session.get(url,headers=headers,auth={userId,password})
    # Read the response
    return resp


def nonprintpost(url, userId, password, data):
    """Helps in making POST REST calls without outputs"""

    if userId is None:
        userId = 'admin'

    if password is None:
        password = 'admin'

    headers = {}
    headers['Content-Type'] = 'application/json'
    # headers['Accept']= 'application/xml'

    session = _cache.switch("CLUSTERING_POST")
    resp = session.post(url, data.encode('utf-8'), headers=headers, auth=(userId, password))

    return resp


def post(url, userId, password, data):
    """Helps in making POST REST calls"""

    if userId is None:
        userId = 'admin'

    if password is None:
        password = 'admin'

    print("post request with url "+url)
    print("post request with data "+data)
    headers = {}
    headers['Content-Type'] = 'application/json'
    # headers['Accept']= 'application/xml'
    session = _cache.switch("CLUSTERING_POST")
    resp = session.post(url, data.encode('utf-8'), headers=headers, auth=(userId, password))

    # print (resp.raise_for_status())
    print (resp.headers)
    if resp.status_code >= 500:
        print(resp.text)

    return resp


def delete(url, userId='admin', password='admin'):
    """Helps in making DELET REST calls"""
    print("delete all resources belonging to url"+url)
    session = _cache.switch("CLUSTERING_DELETE")
    resp = session.delete(url, auth=(userId, password))  # noqa


def Should_Not_Be_Type_None(var):
    '''Keyword to check if the given variable is of type NoneType.  If the
        variable type does match  raise an assertion so the keyword will fail
    '''
    if var is None:
        raise AssertionError('the variable passed was type NoneType')
    return 'PASS'


def execute_ssh_command(ip, username, password, command):
    """Execute SSH Command

    use username and password of controller server for ssh and need
    karaf distribution location like /root/Documents/dist
    """
    print "executing ssh command"
    lib = SSHLibrary()
    lib.open_connection(ip)
    lib.login(username=username, password=password)
    print "login done"
    cmd_response = lib.execute_command(command)
    print "command executed : " + command
    lib.close_connection()
    return cmd_response


def wait_for_controller_up(ip, port="8181"):
    url = "http://" + ip + ":" + str(port) + \
          "/restconf/config/opendaylight-inventory:nodes/node/controller-config/yang-ext:mount/config:modules"

    print "Waiting for controller " + ip + " up."
    # Try 30*10s=5 minutes for the controller to be up.
    for i in xrange(30):
        try:
            print "attempt " + str(i) + " to url " + url
            resp = get(url, "admin", "admin")
            print "attempt " + str(i) + " response is " + str(resp)
            print resp.text
            if ('clustering-it-provider' in resp.text):
                print "Wait for controller " + ip + " succeeded"
                return True
        except Exception as e:
            print e
        time.sleep(10)

    print "Wait for controller " + ip + " failed"
    return False


def startAllControllers(username, password, karafhome, port, *ips):
    # Start all controllers
    for ip in ips:
        execute_ssh_command(ip, username, password, karafhome+"/bin/start")

    # Wait for all of them to be up
    for ip in ips:
        rc = wait_for_controller_up(ip, port)
        if rc is False:
            return False
    return True


def startcontroller(ip, username, password, karafhome, port):
    execute_ssh_command(ip, username, password, karafhome + "/bin/start")
    return wait_for_controller_up(ip, port)


def stopcontroller(ip, username, password, karafhome):
    executeStopController(ip, username, password, karafhome)

    wait_for_controller_stopped(ip, username, password, karafhome)


def executeStopController(ip, username, password, karafhome):
    execute_ssh_command(ip, username, password, karafhome+"/bin/stop")


def stopAllControllers(username, password, karafhome, *ips):
    for ip in ips:
        executeStopController(ip, username, password, karafhome)

    for ip in ips:
        wait_for_controller_stopped(ip, username, password, karafhome)


def wait_for_controller_stopped(ip, username, password, karafHome):
    lib = SSHLibrary()
    lib.open_connection(ip)
    lib.login(username=username, password=password)

    # Wait 1 minute for the controller to stop gracefully
    tries = 20
    i = 1
    while i <= tries:
        stdout = lib.execute_command("ps -axf | grep karaf | grep -v grep | wc -l")
        # print "stdout: "+stdout
        processCnt = stdout[0].strip('\n')
        print("processCnt: " + processCnt)
        if processCnt == '0':
            break
        i = i + 1
        time.sleep(3)

    lib.close_connection()

    if i > tries:
        print "Killing controller"
        kill_controller(ip, username, password, karafHome)


def clean_journal(ip, username, password, karafHome):
    execute_ssh_command(ip, username, password, "rm -rf " + karafHome + "/journal")


def kill_controller(ip, username, password, karafHome):
    execute_ssh_command(ip, username, password,
                        "ps axf | grep karaf | grep -v grep | awk '{print \"kill -9 \" $1}' | sh")


def isolate_controller(controllers, username, password, isolated):
    """ Isolate one controller from the others in the cluster

    :param controllers: A list of ip addresses or host names as strings.
    :param username: Username for the controller to be isolated.
    :param password: Password for the controller to be isolated.
    :param isolated: Number (starting at one) of the controller to be isolated.
    :return: If successful, returns "pass", otherwise returns the last failed IPTables text.
    """
    isolated_controller = controllers[isolated-1]
    for controller in controllers:
        if controller != isolated_controller:
            base_str = 'sudo iptables -I OUTPUT -p all --source '
            cmd_str = base_str + isolated_controller + ' --destination ' + controller + ' -j DROP'
            execute_ssh_command(isolated_controller, username, password, cmd_str)
            cmd_str = base_str + controller + ' --destination ' + isolated_controller + ' -j DROP'
            execute_ssh_command(isolated_controller, username, password, cmd_str)
    ip_tables = execute_ssh_command(isolated_controller, username, password, 'sudo iptables -L')
    print ip_tables
    iso_result = 'pass'
    for controller in controllers:
        controller_regex_string = "[\s\S]*" + isolated_controller + " *" + controller + "[\s\S]*"
        controller_regex = re.compile(controller_regex_string)
        if not controller_regex.match(ip_tables):
            iso_result = ip_tables
        controller_regex_string = "[\s\S]*" + controller + " *" + isolated_controller + "[\s\S]*"
        controller_regex = re.compile(controller_regex_string)
        if not controller_regex.match(ip_tables):
            iso_result = ip_tables
    return iso_result


def rejoin_controller(controllers, username, password, isolated):
    """ Return an isolated controller to the cluster.

    :param controllers: A list of ip addresses or host names as strings.
    :param username: Username for the isolated controller.
    :param password: Password for the isolated controller.
    :param isolated: Number (starting at one) of the isolated controller isolated.
    :return: If successful, returns "pass", otherwise returns the last failed IPTables text.
    """
    isolated_controller = controllers[isolated-1]
    for controller in controllers:
        if controller != isolated_controller:
            base_str = 'sudo iptables -D OUTPUT -p all --source '
            cmd_str = base_str + isolated_controller + ' --destination ' + controller + ' -j DROP'
            execute_ssh_command(isolated_controller, username, password, cmd_str)
            cmd_str = base_str + controller + ' --destination ' + isolated_controller + ' -j DROP'
            execute_ssh_command(isolated_controller, username, password, cmd_str)
    ip_tables = execute_ssh_command(isolated_controller, username, password, 'sudo iptables -L')
    print ip_tables
    iso_result = 'pass'
    for controller in controllers:
        controller_regex_string = "[\s\S]*" + isolated_controller + " *" + controller + "[\s\S]*"
        controller_regex = re.compile(controller_regex_string)
        if controller_regex.match(ip_tables):
            iso_result = ip_tables
        controller_regex_string = "[\s\S]*" + controller + " *" + isolated_controller + "[\s\S]*"
        controller_regex = re.compile(controller_regex_string)
        if controller_regex.match(ip_tables):
            iso_result = ip_tables
    return iso_result


def flush_iptables(controllers, username, password):
    """Removes all entries from IPTables on all controllers.

    :param controllers: A list of ip address or host names as strings.
    :param username: Username for all controllers.
    :param password: Password for all controllers.
    :return: If successful, returns "pass", otherwise returns "fail".
    """
    flush_result = 'pass'
    for controller in controllers:
        print 'Flushing ' + controller
        cmd_str = 'sudo iptables -v -F'
        cmd_result = execute_ssh_command(controller, username, password, cmd_str)
        print cmd_result
        success_string = "Flushing chain `INPUT'" + "\n"
        success_string += "Flushing chain `FORWARD'" + "\n"
        success_string += "Flushing chain `OUTPUT'"
        if not cmd_result == success_string:
            flush_result = "Failed to flush IPTables. Check Log."
        print "."
        print "."
        print "."
    return flush_result


#
# main invoked
if __name__ != "__main__":
    _cache = robot.utils.ConnectionCache('No sessions created')
    # here create one session for each HTTP functions
    _cache.register(requests.session(), alias='CLUSTERING_GET')
    _cache.register(requests.session(), alias='CLUSTERING_POST')
    _cache.register(requests.session(), alias='CLUSTERING_DELETE')
