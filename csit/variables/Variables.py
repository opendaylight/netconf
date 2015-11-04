"""
Definitions of common variables for the system test robot suites of the
OpenDaylight project.

Authors: Baohua Yang@IBM, Denghui Huang@IBM
Edited: Many times by many people
"""

# VM Environment defaults
DEFAULT_LINUX_PROMPT = '>'
DEFAULT_USER = 'jenkins'
DEFAULT_TIMEOUT = '30s'

# ODL system variables
ODL_SYSTEM_IP = '127.0.0.1'  # Override if ODL is not running locally to pybot
ODL_SYSTEM_IP_LIST = ['ODL_SYSTEM_1_IP', 'ODL_SYSTEM_2_IP', 'ODL_SYSTEM_3_IP']
ODL_SYSTEM_USER = DEFAULT_USER
ODL_SYSTEM_PASSWORD = ''  # empty means use public key authentication
ODL_SYSTEM_PROMPT = DEFAULT_LINUX_PROMPT

# "Tools" system variables (mininet etc).
TOOLS_SYSTEM_IP = '127.0.0.1'  # Override if tools are not run locally to pybot
TOOLS_SYSTEM_USER = DEFAULT_USER
TOOLS_SYSTEM_PASSWORD = ''  # empty means use public key authentication
TOOLS_SYSTEM_PROMPT = DEFAULT_LINUX_PROMPT

# KARAF Variables
KARAF_SHELL_PORT = '8101'
ESCAPE_CHARACTER = '\x1B'
KARAF_DETAILED_PROMPT = '@' + ESCAPE_CHARACTER + '[0m' + ESCAPE_CHARACTER + '[34mroot' + ESCAPE_CHARACTER + '[0m>'
KARAF_USER = 'karaf'
KARAF_PASSWORD = 'karaf'
KARAF_PROMPT = 'opendaylight-user'

# Logging levels
DEFAULT_ODL_LOG_LEVEL = 'INFO'
DEFAULT_BGPCEP_LOG_LEVEL = DEFAULT_ODL_LOG_LEVEL
DEFAULT_PROTOCOL_LOG_LEVEL = DEFAULT_BGPCEP_LOG_LEVEL
BGPCEP_LOG_LEVEL = DEFAULT_BGPCEP_LOG_LEVEL
PROTOCOL_LOG_LEVEL = BGPCEP_LOG_LEVEL

# BGP variables
ODL_BGP_PORT = '1790'
BGP_TOOL_PORT = '17900'

# Netconf variables
ODL_NETCONF_PORT = '2830'
ODL_NETCONF_USER = 'admin'
ODL_NETCONF_PASSWORD = 'admin'
ODL_NETCONF_PROMPT = ']]>]]>'
ODL_NETCONF_NAMESPACE = 'urn:ietf:params:xml:ns:netconf:base:1.0'

# VTN Coordinator Variables
VTNC = '127.0.0.1'
VTNCPORT = '8083'
VTNC_PREFIX = 'http://' + VTNC + ':' + VTNCPORT
VTNC_HEADERS = {'Content-Type': 'application/json',
                'username': 'admin', 'password': 'adminpass'}

VTNWEBAPI = '/vtn-webapi'
# controllers URL
CTRLS_CREATE = 'controllers.json'
CTRLS = 'controllers'
SW = 'switches'

# vtn URL
VTNS_CREATE = 'vtns.json'
VTNS = 'vtns'

# vbridge URL
VBRS_CREATE = 'vbridges.json'
VBRS = 'vbridges'

# interfaces URL
VBRIFS_CREATE = 'interfaces.json'
VBRIFS = 'interfaces'

# portmap URL
PORTMAP_CREATE = 'portmap.json'

# vlanmap URL
VLANMAP_CREATE = 'vlanmaps.json'

# ports URL
PORTS = 'ports/detail.json'

# flowlist URL
FLOWLISTS_CREATE = 'flowlists.json'

# flowlistentry_URL
FLOWLISTENTRIES_CREATE = 'flowlistentries.json'
FLOWLISTS = 'flowlists'

# flowfilter_URL
FLOWFILTERS_CREATE = 'flowfilters.json'
FLOWFILTERENTRIES_CREATE = 'flowfilterentries.json'
FLOWFILTERS = 'flowfilters/in'
FLOWFILTERS_UPDATE = 'flowfilterentries'


# Common APIs
CONFIG_NODES_API = '/restconf/config/opendaylight-inventory:nodes'
OPERATIONAL_NODES_API = '/restconf/operational/opendaylight-inventory:nodes'
OPERATIONAL_NODES_NETVIRT = '/restconf/operational/network-topology:network-topology/topology/netvirt:1'
OPERATIONAL_TOPO_API = '/restconf/operational/network-topology:' \
                       'network-topology'
CONFIG_TOPO_API = '/restconf/config/network-topology:network-topology'
CONTROLLER_CONFIG_MOUNT = ('/restconf/config/network-topology:'
                           'network-topology/topology'
                           '/topology-netconf/node/'
                           'controller-config/yang-ext:mount')
CONFIG_API = '/restconf/config'
OPERATIONAL_API = '/restconf/operational'
MODULES_API = '/restconf/modules'

# TOKEN
AUTH_TOKEN_API = '/oauth2/token'
REVOKE_TOKEN_API = '/oauth2/revoke'

# Base Mininet Mac address. DPID of mininet switches will be derived from this.
BASE_MAC_1 = '00:4b:00:00:00:00'
# Base IP of mininet hosts
BASE_IP_1 = '75.75.0.0'

# Vlan Custom Topology Path and File
CREATE_VLAN_TOPOLOGY_FILE = "vlan_vtn_test.py"
CREATE_VLAN_TOPOLOGY_FILE_PATH = "MininetTopo/" +\
                                 CREATE_VLAN_TOPOLOGY_FILE

# Mininet Custom Topology Path and File
CREATE_FULLYMESH_TOPOLOGY_FILE = "create_fullymesh.py"
CREATE_FULLYMESH_TOPOLOGY_FILE_PATH = "libraries/MininetTopo/" +\
                                      CREATE_FULLYMESH_TOPOLOGY_FILE

# Mininet Custom Topology Path and File for Path Policy
CREATE_PATHPOLICY_TOPOLOGY_FILE = "topo-3sw-2host_multipath.py"
CREATE_PATHPOLICY_TOPOLOGY_FILE_PATH = "MininetTopo/" +\
                                       CREATE_PATHPOLICY_TOPOLOGY_FILE

GBP_REGEP_API = "/restconf/operations/endpoint:register-endpoint"
GBP_UNREGEP_API = "/restconf/operations/endpoint:unregister-endpoint"
GBP_TENANTS_API = "/restconf/config/policy:tenants"
GBP_TUNNELS_API = "/restconf/config/opendaylight-inventory:nodes"

# LISP Flow Mapping variables
LFM_RPC_API = "/restconf/operations/mappingservice"
LFM_RPC_API_LI = "/restconf/operations/lfm-mapping-database"
LFM_SB_RPC_API = "/restconf/operations/lisp-sb"

# Other global variables
# TODO: Move these to more apropriate sections.
PORT = '8080'
RESTPORT = '8282'
RESTCONFPORT = '8181'
CONTAINER = 'default'
PREFIX = 'http://' + ODL_SYSTEM_IP + ':' + PORT  # TODO: determine where this is used; create a better named variable
USER = 'admin'  # TODO: who is using this?  Can we make it more specific? (e.g.  RESTCONF_USER)
PWD = 'admin'
PASSWORD = 'EMPTY'
AUTH = [u'admin', u'admin']
SCOPE = 'sdn'
HEADERS = {'Content-Type': 'application/json'}
HEADERS_XML = {'Content-Type': 'application/xml'}
ACCEPT_XML = {'Accept': 'application/xml'}
ACCEPT_JSON = {'Accept': 'application/json'}
ODL_CONTROLLER_SESSION = None
TOPO_TREE_LEVEL = 2
TOPO_TREE_DEPTH = 3
TOPO_TREE_FANOUT = 2
KEYFILE_PASS = 'any'
SSH_KEY = 'id_rsa'
CONTROLLER_STOP_TIMEOUT = 120  # Max number of seconds test will wait for a controller to stop
TOPOLOGY_URL = 'network-topology:network-topology/topology'
SEND_ACCEPT_XML_HEADERS = {'Content-Type': 'application/xml', 'Accept': 'application/xml'}

# Deprecated old variables, to be removed once all tests that need them are
# updated to use the new names.
CONTROLLER = ODL_SYSTEM_IP
CONTROLLERS = ['CONTROLLER', 'CONTROLLER1', 'CONTROLLER2']
CONTROLLER_PASSWORD = ODL_SYSTEM_PASSWORD
CONTROLLER_PROMPT = ODL_SYSTEM_PROMPT
MININET = TOOLS_SYSTEM_IP
MININET_USER = TOOLS_SYSTEM_USER
MININET_PASSWORD = TOOLS_SYSTEM_PASSWORD
MININET_PROMPT = TOOLS_SYSTEM_PROMPT
PROMPT = '>'  # TODO: remove this as it's vague.  need to fix any occurances of it first.
