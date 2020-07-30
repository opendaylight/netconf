Manage NETCONF Devices
======================

Users can manage a NETCONF-enabled device via a NETCONF southbound interface. Communication
between devices is session based. That is, a connection and session are established before
exchanging data. The session closes when the exchange of data completes. Users can connect and
manage to any device implementing a NETCONF server that adheres to the IETF specifications. In
addition, a RESTCONF interface is used to manage NETCONF devices at run time.

Configuring a NETCONF-enabled Device
------------------------------------

By default, the config-subsystem NETCONF server is not mounted to a controller.
To view and change config details, mount the controller to the config-subsystem
NETCONF server by sending the following request:

**Headers:**

- **Content-type:** ``application/xml``

- **Accept:** ``application/xml``

- **Authentication:** ``admin:admin``

**URL:** ``/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config``

**Method:** ``PUT``

**Payload:**

.. code-block:: console

   <node xmlns="urn:TBD:params:xml:ns:yang:network-topology">
   <node-id>controller-config</node-id>
   <host xmlns="urn:opendaylight:netconf-node-topology">127.0.0.1</host>
   <port xmlns="urn:opendaylight:netconf-node-topology">1830</port>
   <username xmlns="urn:opendaylight:netconf-node-topology">admin</username>
   <password xmlns="urn:opendaylight:netconf-node-topology">admin</password>
   <tcp-only xmlns="urn:opendaylight:netconf-node-topology">false</tcp-only>
   <keepalive-delay xmlns="urn:opendaylight:netconf-node-topology">0</keepalive-delay>
   </node>

Once the controller config-subsystem NETCONF server mounts to the
controller, configuration details of all modules in the controller are
available at the following URL: ``/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config/yang-ext:mount/``.

Connecting to a NETCONF-enabled Device
--------------------------------------

The following example connects to a NETCONF-enabled device without a password.

**Headers:**

- **Content-type:** ``application/xml``
- **Accept:** ``application/xml``
- **Authentication:** ``admin:admin``

**URL:** ``/restconf/config/network-topology:network-topology/topology/topology-netconf/node/<mount-name>``

**Method:** ``PUT``

**Payload:**

.. code-block:: console

   <node xmlns="urn:TBD:params:xml:ns:yang:network-topology">
   <node-id>vyatta</node-id>
   <host xmlns="urn:opendaylight:netconf-node-topology">netconf-device-ip</host>
   <port xmlns="urn:opendaylight:netconf-node-topology">22</port>
   <username xmlns="urn:opendaylight:netconf-node-topology">admin</username>
   <password xmlns="urn:opendaylight:netconf-node-topology">admin</password>
   <tcp-only xmlns="urn:opendaylight:netconf-node-topology">false</tcp-only>
   <keepalive-delay xmlns="urn:opendaylight:netconf-node-topology">0</keepalive-delay>
   </node>

Modify the following parameters to match those on the device:

* ``node-id``
* ``host``
* ``port``
* ``username``
* ``password``

The ``node-id`` value must match the string used for <mount-name> in the URL.
It serves as the identifier of a mounted device. After issuing this request,
A NETCONF connector spawns immediately. A moment may pass before the NETCONF
device successfully connects to download all necessary schemas.

.. note:: The above RESTCONF request URL is in the ``bierman02`` format. The `RESTCONF
   RFC 8040 <https://tools.ietf.org/html/rfc8040>`_ functionality is available but
   uses a different format.

Connecting a NETCONF-enabled Device with an Encrypted Password
--------------------------------------------------------------

The following example connects a NETCONF-enabled device using the
create-device RPC. This RPC encrypts the password on the datastore.

**Headers:**

- **Content-type:** ``application/json``

- **Accept:** ``application/json``

- **Authentication:** ``admin:admin``

**URL:** ``http://controller:8181/restconf/operations/netconf-node-topology:create-device``

**Method:** ``POST``

**Body:**

.. code-block:: console

   {
    "input": {
        "netconf-node-topology:node-id": "VMX-99",
        "host": "172.31.11.56",
        "port": "830",
        "username": "lumina",
        "password": "lumina1",
        "tcp-only": "false",
        "keepalive-delay": "0"
      }
   }

Modify the following parameters to match those on the device:

* ``node-id``
* ``host``
* ``port``
* ``username``
* ``password``

The ``node-id`` value must match the string used for <mount-name> in the URL.
It serves as the identifier of a mounted device. After issuing this request,
A NETCONF connector spawns immediately. A moment may pass before the NETCONF
device successfully connects to download all necessary schemas.

**Payload**

.. code-block:: console

   {
    "node-id": "VMX-99",
    "netconf-node-topology:reconnect-on-changed-schema": false,
    "netconf-node-topology:concurrent-rpc-limit": 0,
    "netconf-node-topology:password": "lumina1",
    "netconf-node-topology:username": "lumina",
    "netconf-node-topology:tcp-only": false,
    "netconf-node-topology:max-connection-attempts": 0,
    "netconf-node-topology:keepalive-delay": 0,
    "netconf-node-topology:schemaless": false,
    "netconf-node-topology:schema-cache-directory": "schema",
    "netconf-node-topology:default-request-timeout-millis": 60000,
    "netconf-node-topology:sleep-factor": "1.5",
    "netconf-node-topology:port": 830,
    "netconf-node-topology:connection-timeout-millis": 20000,
    "netconf-node-topology:host": "172.31.11.56",
    "netconf-node-topology:actor-response-wait-time": 5,
    "netconf-node-topology:between-attempts-timeout-millis": 2000
   },

Modifying a NETCONF-enabled Mounted Device
------------------------------------------

After mounting and connecting to a NETCONF device, users can change the
configuration parameters at run time. For example, changing the username
or password of a mounted NETCONF device. The following example show how
to change the username and password of a mounted device named ``new-netconf-device``.

**Headers:**

- **Content-type:** ``application/xml``

- **Accept:** ``application/xml``

- **Authentication:** ``admin:admin``

**URL:** ``/restconf/config/network-topology:network-topology/topology/topology-netconf/node/new-netconf-device``

**Method:** ``PUT``

**Payload:**

.. code-block:: console

   <node xmlns="urn:TBD:params:xml:ns:yang:network-topology">
   <node-id>new-netconf-device</node-id>
   <host xmlns="urn:opendaylight:netconf-node-topology">new-netconf-device-ip</host>
   <port xmlns="urn:opendaylight:netconf-node-topology">22</port>
   <username xmlns="urn:opendaylight:netconf-node-topology">admin</username>
   <password xmlns="urn:opendaylight:netconf-node-topology">admin</password>
   <tcp-only xmlns="urn:opendaylight:netconf-node-topology">false</tcp-only>
   <keepalive-delay xmlns="urn:opendaylight:netconf-node-topology">0</keepalive-delay>
   </node>

Modifying a NETCONF-enabled Device with an Encrypted Password
-------------------------------------------------------------

Send the following request to change configuration parameters of a mounted
NETCONF device with an encrypted password:

**Headers:**

- **Content-type:** ``application/json``

- **Accept:** ``application/json``

- **Authentication:** ``admin:admin``

**URL:** ``http://controller:8181/restconf/operations/netconf-node-topology:create-device``

**Method:** ``POST``

**Payload:**

.. code-block:: console

   {
    "input": {
        "netconf-node-topology:node-id": "VMX-99",
        "host": "172.31.11.56",
        "port": "830",
        "username": "lumina",
        "password": "lumina1",
        "tcp-only": "false",
        "keepalive-delay": "0"
      }
   }

Modify the following parameters to match those on the device:

* ``node-id``
* ``host``
* ``port``
* ``username``
* ``password``

The ``node-id`` value must match the string used for <mount-name> in the URL.
It serves as the identifier of a mounted device. After issuing this request,
A NETCONF connector spawns immediately. A moment may pass before the NETCONF
device successfully connects to download all necessary schemas.

Creating a NETCONF-connector
----------------------------

Before creating added NETCONF-connectors, users must install a `netconf-connector` in Karaf.
Enter the following at the Karaf prompt to install a NETCONF connector:

.. code-block:: none

   feature:install odl-netconf-connector-all

The loopback mountpoint is automatically configured and activated.
Wait until the log displays the following:

.. code-block:: none

   RemoteDevice{controller-config}: NETCONF connector initialized successfully

Send the following request to RESTCONF to configure a new NETCONF-connector:

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config/yang-ext:mount/config:modules``

**Method:** POST

**Payload:**  

.. code-block:: none

.. code-block:: none

   <module xmlns="urn:opendaylight:params:xml:ns:yang:controller:config">
   <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">prefix:sal-netconf-connector</type>
   <name>new-netconf-device</name>
   <address xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">127.0.0.1</address>
   <port xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">830</port>
   <username xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">admin</username>
   <password xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">admin</password>
   <tcp-only xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">false</tcp-only>
   <event-executor xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
    <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:netty">prefix:netty-event-executor</type>
    <name>global-event-executor</name>
   </event-executor>
   <binding-registry xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
    <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:md:sal:binding">prefix:binding-broker-osgi-registry</type>
    <name>binding-osgi-broker</name>
   </binding-registry>
   <dom-registry xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
    <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:md:sal:dom">prefix:dom-broker-osgi-registry</type>
    <name>dom-broker</name>
   </dom-registry> 
   <client-dispatcher xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
    <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:config:netconf">prefix:netconf-client-dispatcher</type>
    <name>global-netconf-dispatcher</name>
   </client-dispatcher> 
   <processing-executor xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
    <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:threadpool">prefix:threadpool</type>
    <name>global-netconf-processing-executor</name>
   </processing-executor>
   <keepalive-executor xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
    <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:threadpool">prefix:scheduled-threadpool</type>
    <name>global-netconf-ssh-scheduled-executor</name>
   </keepalive-executor>
   </module> 

Verify Configuration
^^^^^^^^^^^^^^^^^^^^

A new NETCONF-connector is created. It attempts to connect to (or mount) to a NETCONF device at
127.0.0.1 and port 830. Send the following command to check the configuration of config-subsystem’s
configuration datastore:

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config/yang-ext:mount/config:modules``

**Method:** GET

The response will contain the module for new-NETCONF-device.

Discover a Configuration
^^^^^^^^^^^^^^^^^^^^^^^^

After creating the new NETCONF-connector, it writes some useful metadata into the datastore of
MD-SAL under the network-topology subtree. This metadata can be found at:

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/operational/network-topology:network-topology/``

**Method:** GET

Information about connection status, device capabilities, etc. displays.

Reconfiguring the NETCONF-Connector
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Users can change the configuration of a running module even while the controller is running.
The following configuration example change the configuration of a new NETCONF-connector
after it was created. Using one request, this example changes both the username and
password of the NETCONF-connector. Since a ``PUT`` is a replace operation, the configuration
must be specified along with the new values for username and password. This should result in a
response with the NETCONF-connector called ``new-netconf-device`` will be reconfigured to
use username **bob** and password **passwd**.

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config/yang-ext:mount/config:modules/module/odl-sal-netconf-connector-cfg:sal-netconf-connector/new-netconf-device``

**Method:** PUT

**Payload:**

.. code-block:: none

  <module xmlns="urn:opendaylight:params:xml:ns:yang:controller:config">
    <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">prefix:sal-netconf-connector</type>
    <name>new-netconf-device</name>
    <username xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">bob</username>
    <password xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">passwd</password>
    <tcp-only xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">false</tcp-only>
    <event-executor xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
     <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:netty">prefix:netty-event-executor</type>
     <name>global-event-executor</name>
    </event-executor>
    <binding-registry xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
     <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:md:sal:binding">prefix:binding-broker-osgi-registry</type>
     <name>binding-osgi-broker</name>
    </binding-registry>
    <dom-registry xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
     <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:md:sal:dom">prefix:dom-broker-osgi-registry</type>
     <name>dom-broker</name>
    </dom-registry>
    <client-dispatcher xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
     <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:config:netconf">prefix:netconf-client-dispatcher</type>
     <name>global-netconf-dispatcher</name>
    </client-dispatcher>
    <processing-executor xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
     <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:threadpool">prefix:threadpool</type>
     <name>global-netconf-processing-executor</name>
    </processing-executor>
    <keepalive-executor xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
     <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:threadpool">prefix:scheduled-threadpool</type>
     <name>global-netconf-ssh-scheduled-executor</name>
     </keepalive-executor>
    </module>

Deleting a NETCONF-Connector
----------------------------

When a NETCONF-connector module is deleted, the connection is dropped
and all resources are cleaned. Send the following request to delete a
mounted NETCONF device:

**Headers:**

- **Content-type:** ``application/xml``

- **Accept:** ``application/xml``

- **Authentication:** ``admin:admin``

**URL:** ``/restconf/config/network-topology:network-topology/topology/topology-netconf/node/<mount-name>``

**Method:** ``DELETE``

Deleting a NETCONF-Connector When the Controller is Running
-----------------------------------------------------------

Users can delete an instance of a module even while the controller is runner. In this case,
the module is removed, the NETCONF connection is dropped, and all resources are cleaned.
Issue the request to following URL to delete a NETCONF-connector when the controller is running.

**Headers:**

- **Content-type:** ``application/xml``

- **Accept:** ``application/xml``

- **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config/yang-ext:mount/config:modules/module/odl-sal-netconf-connector-cfg:sal-netconf-connector/new-netconf-device``

**Method:** ``DELETE``

The last element of the URL is the instance name and its predecessor are the module type.
In this case, the type is **sal-netconf-connector** and the name is **new-netconf-device**.
The type and name are the keys of the module list.
