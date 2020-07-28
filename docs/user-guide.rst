=======
NETCONF
=======

The Network Configuration (NETCONF) protocol is a network management protocol developed and
standardized by the Internet Engineering Task Force (IETF). NETCONF is designed to install,
update, and delete the configurations of network devices. Operating on top of the Remote
Procedure Call (RPC) layer using XML encoding, NETCONF provides a set of operational tools
that can be used to edit and query configuration data of devices.
NETCONF can operate either as a :ref:`southbound` or as a :ref:`northbound`.

For more information on NETCONF, refer to `RFC 6241 <https://tools.ietf.org/html/rfc6241>`_.
NETCONF can be conceptually partitioned into the following four layers:

.. list-table:: NETCONF Layers 
   :widths: 20 60 
   :header-rows: 1 

   * - **Layer** 
     - **Description** 
   * - **Content layer**
     - This layer includes both configuration and notification data. 
   * - **Operations layer**
     - This layer defines a set of base-protocol operations to retrieve
       and edit config data.
   * - **Messages layer**
     - This layer is a mechanism that encodes RPCs and notifications.
   * - **Secure Transport layer**
     - This layer provides a secure and reliable for messaging between
       clients and servers.

.. _southbound:

Southbound Plugin
-----------------

As a southbound plugin (NETCONF-connector) NETCONF can connect to remote NETCONF
devices to expose their configuration/operational datastores, RPCs, and
notifications as an MD-SAL mount point. Mount points allow apps and remote
users to interact with the mounted devices over RESTCONF. The connector
supports these added RPCs:

* `RFC-5277 <http://tools.ietf.org/html/rfc5277>`_
* `RFC-6022 <http://tools.ietf.org/html/rfc6022>`_
* `draft-ietf-netconf-yang-library-06 <https://tools.ietf.org/html/draft-ietf-netconf-yang-library-06>`_

By using the YANG modeling language, the NETCONF-connector is fully model-driven.
Thus, in addition to the above RFCs, it also supports any data/RPC/notifications that
are described by the YANG model implemented in devices. To activate the NETCONF
southbound plugin, install the ``odl-netconf-connector-all`` Karaf feature. Refer to
:ref:`create-added-netconf`.
 
NETCONF-Connector
-----------------

Users can configure the NETCONF-connector using either NETCONF or RESTCONF; however,
this guide focuses on using RESTCONF. In addition, there are two different
endpoints related to the RESTCONF protocol:

* ``http://localhost:8181/restconf`` is related to `draft-bierman-netconf-restconf-02
  <https://tools.ietf.org/html/draft-bierman-netconf-restconf-02>`_. It can be activated
  by installing the ``odl-restconf-nb-bierman02`` Karaf feature.

* ``http://localhost:8181/rests`` is related to `RFC-8040 <http://tools.ietf.org/html/rfc8040>`_.
  It can be activated by installing the ``odl-restconf-nb-rfc8040`` Karaf feature.

For `RFC-8040 <http://tools.ietf.org/html/rfc8040>`_, the resources to configure and
operate datastores start with ``/rests/data/``, for example:

* ``GET http://localhost:8181/rests/data/network-topology:network-topology`` with
  response of both datastores; that is, configuration and operational.
* ``GET http://localhost:8181/rests/data/network-topology:network-topology?content=config`` for configuration datastore.
* ``GET http://localhost:8181/rests/data/network-topology:network-topology?content=nonconfig`` for operational datastore.

Default Configuration
^^^^^^^^^^^^^^^^^^^^^

The NETCONF default configuration has all necessary dependencies (such as, ``01-netconf.xml``), as well
as a single instance of NETCONF-connector (such as, ``99-netconf-connector.xml``) called ``controller-config``,
which connects itself to the NETCONF northbound in a loopback fashion. The connector mounts the
NETCONF server for config-subsystem to enable RESTCONF protocol for config-subsystem.

Managing Devices
----------------

Users can manage a NETCONF-enabled device via a NETCONF southbound interface. Communication
between devices is session based. That is, a connection and session are established before
exchanging data. The session closes when the exchange of data completes. Users can connect and
manage to any device implementing a NETCONF server that adheres to the IETF specifications. In 
addition, a RESTCONF interface is used to manage NETCONF devices at run time. 

Users can use RESTCONF to perform the following operations:

- :ref:`configure-device`
- :ref:`connecting-netconf`
- :ref:`connect-not-supporting`
- :ref:`changeing-netconf`
- :ref:`deleting-netconf`
- :ref:`create-added-netconf`

.. _configure-device:

Configuring a NETCONF-enabled Device
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

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

.. _connecting-netconf:

Connecting to a NETCONF-enabled Device
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

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

.. _Connecting-to-a-NETCONF-enabled-device-with-an-encrypted-password:

Connecting a NETCONF-enabled Device with an Encrypted Password
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

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

.. _connect-not-supporting:

Connecting to a Device not Supporting NETCONF 
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The NETCONF-connector uses ``ietf-netconf-monitoring`` support when connecting to
NETCONF-enabled devices. This allows the NETCONF-connector to download the YANG
schemas used by a device. The NETCONF-connector can only communicate with a device
if it knows the schema of the device (or at least a subset). However, some devices use
YANG models internally, so the NETCONF-connector must *side-load* the necessary YANG
models into the NETCONF device’s YANG model cache for communication. In general,
there are two situations that may occur: 

* If the NETCONF device does not support ``ietf-netconf-monitoring`` but lists its YANG models
  as capabilities in HELLO message. This could be a device that internally uses only the
  ``ietf-inet-types`` YANG model with revision 2010-09-24. In the HELLO message sent from
  this device, the following capability is reported: 

  .. code-block:: none 
 
     urn:ietf:params:xml:ns:yang:ietf-inet-types?module=ietf-inet-types&revision=2010-09-24 

  For this type of device, put the schema into the cache/schema folder inside the Karaf distribution. 

  .. important:: The file with the YANG schema for ietf-inet-types must be called
     ietf-inet-types@2010-09-24.yang. It is the required naming format of the cache. 

* If the NETCONF device does not support ``ietf-netconf-monitoring`` nor lists its
  YANG model as capabilities in the HELLO message. Since there is no capability with 
  ``ietf-inet-types`` in the HELLO message and the device has no information about the
  YANG schemas, the user must configure the NETCONF-connector for this device. To do
  this, the NETCONF-connector has an optional configuration attribute called ``yang-module-capabilities``.
  This attribute has a list of YANG module-based capabilities; thus, the user must set this
  configuration attribute to override the yang-module-based capabilities reported in HELLO
  message. To do this, change the NETCONF-connector configuration by adding the following XML.
  This must be added next to the address, port, username etc. configuration elements: 

  .. code-block:: none

     <yang-module-capabilities xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf"> 
      <capability xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf"> 
       urn:ietf:params:xml:ns:yang:ietf-inet-types?module=ietf-inet-types&amp;revision=2010-09-24 
      </capability> 
     </yang-module-capabilities> 

Ensure to put the YANG schemas into the cache folder. 

.. note:: For multiple capabilities, you must replicate the capability XML element inside the
          yang-module-capability element. Capability element is modeled as a leaf-list. This
          configuration makes the remote device report usage of ietf-inet-types to the NETCONF-connector.

.. _changeing-netconf:

Modifying a NETCONF-enabled Mounted Device
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

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
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

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

.. _deleting-netconf:

Deleting a Mounted NETCONF Device
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

When a NETCONF-connector module is deleted, the connection is dropped
and all resources are cleaned. Send the following request to delete a
mounted NETCONF device:

**Headers:**

- **Content-type:** ``application/xml``

- **Accept:** ``application/xml``

- **Authentication:** ``admin:admin``

**URL:** ``/restconf/config/network-topology:network-topology/topology/topology-netconf/node/<mount-name>``

**Method:** ``DELETE``

.. _create-added-netconf:

Creating Additional NETCONF-connectors
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
 
Before creating added NETCONF-connectors, users must install netconf-connector in Karaf.
To do so, type the following at the Karaf prompt: 

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
~~~~~~~~~~~~~~~~~~~~

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

Discover Configuration
~~~~~~~~~~~~~~~~~~~~~~

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

Verify Configuration
~~~~~~~~~~~~~~~~~~~~

The new configuration is created, the old connection closes, and a new connection is established.
Verify the configuration by executing the following:

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/
topology-netconf/node/controller-config/yang-ext:mount/config:modules/module/
odl-sal-netconf-connector-cfg:sal-netconf-connector/new-netconf-device``

**Method:** GET

Deleting a NETCONF-Connector When the Controller is Running
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

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

NETCONF Clustering
------------------

Clustering support for NETCONF is done by installing the ``odl-netconf-clustered-topology``
feature. When a new clustered connector is configured for NETCONF (i.e., when a new
NETCONF-enabled device is mounted), configuration is distributed among the member
nodes and a NETCONF connector is created on each node. From these nodes, a master
is chosen to handle the schema download, as well as to communicate with each device.
When the master node goes down, another node in the cluster takes ownership of the
devices; thus, providing high availability. In addition, since each node in the
cluster takes responsibility for subsets of the connected NETCONF devices,
load balancing is also achieved.

Users can also use the ``odl-netconf-clustered-topology`` feature in a single-node
scenario. However, any code that uses **akka** will be used, so for a scenario where
only a single node is used, ``odl-netconf-topology`` might be preferred.

.. warning:: The ``odl-netconf-topology`` and ``odl-netconf-clustered-topology``
             features are considered incompatible since they both manage the same
             space in the datastore. This would issue conflicting writes when
             installed together.

Configure NETCONF with MD-SAL
-----------------------------

A NETCONF connector can be directly configured through MD-SAL by using the
network-topology model and through the NETCONF server for MD-SAL (port 2830).
To enable NETCONF connector configuration through MD-SAL install either the
``odl-netconf-topology`` or ``odl-netconf-clustered-topology`` feature. In addition,
the ``odl-restconf`` must be installed.

Issue the following command to confirm that both ``odl-netconf-topology`` or
``odl-netconf-clustered-topology`` are installed:

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/operational/network-topology:network-topology/topology/topology-netconf/``

**Method:** ``GET``

**Payload:**

The return should be a non-empty response:

.. code-block:: none

   <topology xmlns="urn:TBD:params:xml:ns:yang:network-topology">
    <topology-id>topology-netconf</topology-id>
   </topology>

Create a NETCONF Connector
^^^^^^^^^^^^^^^^^^^^^^^^^^

There are four ways to create a new NETCONF connector using MD-SAL. In each case,
ensure that the device's name in ``<node-id>`` matches the last element of the URL.

bierman02
~~~~~~~~~

Send the following request to RESTCONF to create a NETCONF connector for bierman02:

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/new-netconf-device``

**Method:** ``PUT``

rfc8040
~~~~~~~

Send the following request to RESTCONF to create new NETCONF connector for rfc8040:

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=new-netconf-device``

**Method:** ``PUT``

bierman02
~~~~~~~~~

When using the same body to create a NETCONF connector without specifying the node in the URL,
send the following request for bierman02:

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf``

**Method:** ``PUT``

rfc8040
~~~~~~~

When using the same body to create a NETCONF connector without specifying the node in the URL,
send the following request for rfc8040

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf``

**Method:** ``PUT``

**Payload:**

The following is the payload for all four of the previous cases:

.. code-block:: none

   <node xmlns="urn:TBD:params:xml:ns:yang:network-topology">
    <node-id>new-netconf-device</node-id>
    <host xmlns="urn:opendaylight:netconf-node-topology">127.0.0.1</host>
    <port xmlns="urn:opendaylight:netconf-node-topology">17830</port>
    <username xmlns="urn:opendaylight:netconf-node-topology">admin</username>
    <password xmlns="urn:opendaylight:netconf-node-topology">admin</password>
    <tcp-only xmlns="urn:opendaylight:netconf-node-topology">false</tcp-only>
    <!-- non-mandatory fields with default values, you can safely remove these if you do not wish to override any of these values-->
    <reconnect-on-changed-schema xmlns="urn:opendaylight:netconf-node-topology">false</reconnect-on-changed-schema>
    <connection-timeout-millis xmlns="urn:opendaylight:netconf-node-topology">20000</connection-timeout-millis>
    <max-connection-attempts xmlns="urn:opendaylight:netconf-node-topology">0</max-connection-attempts>
    <between-attempts-timeout-millis xmlns="urn:opendaylight:netconf-node-topology">2000</between-attempts-timeout-millis>
    <sleep-factor xmlns="urn:opendaylight:netconf-node-topology">1.5</sleep-factor>
    <!-- keepalive-delay set to 0 turns off keepalives-->
    <keepalive-delay xmlns="urn:opendaylight:netconf-node-topology">120</keepalive-delay>
   </node>

Reconfiguring an Existing Connector
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Reconfiguring an existing connector is the same steps as creating a new connector.
After reconfiguring an existing an existing connector, the old connection is
disconnected and a new connector with the new configuration is created. This is
done with a ``PUT`` request since the node already exists. In addition, a ``PATCH``
request can be used to change an existing configuration. The URL would be the same
as the ``PUT`` examples, while using JSON for the body.

**Headers:**

* **Accept:** ``application/yang.patch-status+json``
* **Content-Type:** ``application/yang.patch+json``

**Payload:**

.. code-block:: none

   {
    "ietf-restconf:yang-patch" : {
     "patch-id" : "0",
     "edit" : [
      {
        "edit-id" : "edit1",
        "operation" : "merge",
        "target" : "",
        "value" : {
         "node": [
            {
             "node-id": "new-netconf-device",
             "netconf-node-topology:password" : "newpassword"
            }
         ]
        }
      }
     ]
    }
   }

Deleting a Connector
^^^^^^^^^^^^^^^^^^^^

To remove a configured NETCONF connector, send a DELETE request to the same
URL that was used to create the device:

bierman02
~~~~~~~~~

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/new-netconf-device``

**Method:** ``DELETE``

rfc8040
^^^^^^^

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=new-netconf-device``

Utilize NETCONF-Connection
--------------------------

Once a NETCONF-connector is up-and-running, users can utilize this new mount point instance either by
using RESTCONF or from an application code. For information on NETCONF-Connector mount, refer to the
`Core tutorials project <https://github.com/opendaylight/coretutorials/tree/master/ncmount>`_.

Reading Data from a Device
^^^^^^^^^^^^^^^^^^^^^^^^^^

Invoke the following command to read data from a device:

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8080/restconf/operational/network-topology:network-topology/topology/topology-netconf/node/new-netconf-device/yang-ext:mount/``

**Method:** ``GET``

This returns the entire content of a device's operation datastore. To view just the
configuration datastore, change **operational** to **config** in the URL.

Writing Configuration Data
^^^^^^^^^^^^^^^^^^^^^^^^^^

When writing configuration data, the data must conform to the YANG model implemented by
each device. In the following example, a new ``interface-configuration`` object to the
mounted device (if the device supports the **Cisco-IOS-XR-ifmgr-cfg** YANG model).

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/new-netconf-device/yang-ext:mount/Cisco-IOS-XR-ifmgr-cfg:interface-configurations``

**Method:** ``POST``

**Payload:**

.. code-block:: none

   <interface-configuration xmlns="http://cisco.com/ns/yang/Cisco-IOS-XR-ifmgr-cfg">
    <active>act</active>
    <interface-name>mpls</interface-name>
    <description>Interface description</description>
    <bandwidth>32</bandwidth>
    <link-status></link-status>
   </interface-configuration>

This should return a ``200`` response code with no body. In addition, this call is transformed
into a couple of NETCONF RPCs. Resulting NETCONF RPCs that go into the device can be found in
the OpenDaylight logs after invoking ``log:set TRACE org.opendaylight.controller.sal.connect.netconf``
in the Karaf shell.

Invoking a Custom RPC
^^^^^^^^^^^^^^^^^^^^^

Devices can implement any added RPC whenever YANG models are provided.
The following call invokes the **get-schema** RPC. This call fetches the source
for the ``ietf-yang-types`` YANG model from a mounted device.

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/operations/network-topology:network-topology/topology/topology-netconf/node/new-netconf-device/yang-ext:mount/ietf-netconf-monitoring:get-schema``

**Method:** ``POST``

**Payload:**

.. code-block:: none

   <input xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
    <identifier>ietf-yang-types</identifier>
    <version>2013-07-15</version>
   </input>

NETCONF-connector and Netopeer
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Netopeer is an open-source NETCONF server. It can be used to test/explore NETCONF
southbound. For information on installing Netopeer, refer to `Set up Netopeer Server
<http://www.seguesoft.com/index.php/how-to-set-up-netopeer-server-to-use-with-netconfc>`_.
Before using Netopeer, ensure that both ``odl-restconf-all`` and ``odl-netconf-connector-all``
are installed, and that Netopeer is up-and-running in Docker. Send the following request to
RESTCONF to create a new NETCONF connector using MD-SAL. Ensure that the device's name in
``<node-id>`` matches the last element of the URL.

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8131/restconf/operational/network-topology:network-topology/topology/netopeer/``

**Method:** ``PUT``

After Netopeer is mounted successfully, read its configuration by invoking the following:

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/netopeer/yang-ext:mount/``

**Method:** ``GET``

.. _northbound:

Northbound Server
-----------------

A northbound server acta as a northbound interface for NETCONF. There are two types of NETCONF
servers: :ref:`config-subsystem` and :ref:`config-mdsal`.

.. _config-subsystem:

NETCONF Server for Config-subsystem
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

By default, the NETCONF server for config-subsystem listens on port 1830.
It serves as a default interface for config-subsystem to allow users to create,
reconfigure, and delete modules or applications.

.. _config-mdsal:

NETCONF Server for MD-SAL
^^^^^^^^^^^^^^^^^^^^^^^^^

The NETCONF server for MD-SAL listens on port 2830. This server acts as an alternative
interface for MD-SAL (besides RESTCONF) to allow users to read/write data from
MD-SAL’s datastore and to invoke its RPC. It is recommended using RESTCONF with
the controller-config loopback mountpoint, instead of using just NETCONF.
The NETCONF server for MD-SAL uses a standard MD-SAL API to act as an alternative
to RESTCONF. It is fully model driven to support any data and RPC supported by MD-SAL.
Install NETCONF northbound for MD-SAL by installing the ``odl-netconf-mdsal`` feature
in Karaf. Default binding port is 2830.

Default configuration can be found in the ``08-netconf-mdsal.xml`` file. This file
contains the configuration for all necessary dependencies, as well as a single SSH
endpoint starting on port 2830. There is also a TCP endpoint, which is disabled by
default. It is possible to start multiple endpoints simultaneously.

Verifying NETCONF Server
^^^^^^^^^^^^^^^^^^^^^^^^

After the NETCONF server is available, it can be examinedon the command line by
using the SSH tool. The server responds by sending a HELLO message, which can then
be used as a regular NETCONF server.

.. code-block:: none

   ssh admin@localhost -p 2830 -s netconf

Mounting NETCONF Server
^^^^^^^^^^^^^^^^^^^^^^^

Issue the following call to mount the NETCONF server for MD-SAL.

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://127.0.0.1:2830/restconf/operational/network-topology:network-topology/topology/controller-mdsal/``

**Method:** ``PUT``

After mounted successfully the NETCONF server, read its configuration by invoking the following:

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/operational/network-topology:network-topology/topology/topology-netconf/node/controller-mdsal/yang-ext:mount``

**Method:** ``GET``

YANGLIB Remote Repository
-------------------------

Some scenarios in NETCONF deployment require a centralized YANG models repository.
The `YANGLIB plugin <https://cocoapods.org/pods/YangLib>`_ provides this type of
remote repository. To start this plugin, install the ``odl-yanglib`` feature and
configure it using RESTCONF.

Configuring YANGLIB
^^^^^^^^^^^^^^^^^^^

To configure YANGLIB, specify the local YANG module directory that will be used.
Then specify the address and port of where to provide the YANG sources.
In the following example, the YANG sources are from **/sources** folder on

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config/yang-ext:mount/config:modules/module/yanglib:yanglib/example``

**Method:** ``PUT``

**Payload:**

.. code-block:: none

   <module xmlns="urn:opendaylight:params:xml:ns:yang:controller:config">
    <name>example</name>
    <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:yanglib:impl">prefix:yanglib</type>
    <broker xmlns="urn:opendaylight:params:xml:ns:yang:controller:yanglib:impl">
    <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:md:sal:binding">prefix:binding-broker-osgi-registry</type>
    <name>binding-osgi-broker</name>
   </broker>
   <cache-folder xmlns="urn:opendaylight:params:xml:ns:yang:controller:yanglib:impl">/sources</cache-folder>
   <binding-addr xmlns="urn:opendaylight:params:xml:ns:yang:controller:yanglib:impl">localhost</binding-addr>
   <binding-port xmlns="urn:opendaylight:params:xml:ns:yang:controller:yanglib:impl">5000</binding-port>
   </module>

This results in a new YANGLIB instance. This YANGLIB takes all YANG sources from
/sources folder and for each generates URL in form: ``http://localhost:5000/schemas/{modelName}/{revision}``
This URL will host the YANG source for this module. The YANGLIB instance also writes this URL,
along with source identifier to the ``ietf-netconf-yang-library/modules-state/module`` list.

NETCONF-Connector with the YANG Library
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The YANG library is an optional configuration in the NETCONF-connector. You can specify the YANG
library to be plugged as an added source provider into the mount’s schema repository. Since
YANGLIB plugin is advertising its provided modules through YANG-library model, you can use it in
the mount point’s configuration as YANG library. To do this, change the NETCONF-connector
configuration by adding the following XML. This registers the YANGLIB provided source as
a fallback schema for a mount point.

.. code-block:: none

   <yang-library xmlns="urn:opendaylight:netconf-node-topology">
    <yang-library-url xmlns="urn:opendaylight:netconf-node-topology">http://localhost:8181/restconf/operational/ietf-yang-library:modules-state</yang-library-url>
    <username xmlns="urn:opendaylight:netconf-node-topology">admin</username>
    <password xmlns="urn:opendaylight:netconf-node-topology">admin</password>
   </yang-library>


NETCONF Call-Home
-----------------

The NETCONF Call-Home enables NETCONF to initiate a secure connection to a NETCONF-enabled device.
It is defined in `RFC 8071 <https://tools.ietf.org/html/rfc8071>`_. and is installed in Karaf when
installing the ``odl-netconf-callhome-ssh`` feature. Use Netopeer to test the Call-Home functionality.
Refer to `Netopeer Call-Home <https://github.com/CESNET/netopeer/wiki/CallHome>`_ to learn how to enable
call-home on Netopeer.

Northbound Call-Home API
^^^^^^^^^^^^^^^^^^^^^^^^

The northbound Call-Home API is used for administering the Call-Home server. The Call-Home server allows
user to configure global credentials, which will be used for devices that do not have device-specific
credentials configured. This is done by creating a ``/odl-netconf-callhome-server:netconf-callhome-server/global/credentials``, with username and passwords specified.
Issue the following command to configure global username and password:

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

**URL:** ``/restconf/config/odl-netconf-callhome-server:netconf-callhome-server/global/credentials HTTP/1.1``

**Method:** ``PUT``

**Payload:**

.. code-block:: none

   {
    "credentials":
    {
    "username": "example",
    "passwords": [ "first-password-to-try", "second-password-to-try" ]
    }
   }

Configure SSH Server
~~~~~~~~~~~~~~~~~~~~

Users can configure to accept any SSH server key using global credentials. By default,
the NETCONF Call-Home Server accepts only incoming connections from allowed devices on 
``/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices``. To allow all
incoming connections, set the ``accept-all-ssh-keys`` to **true** in the 
``/odl-netconf-callhome-server:netconf-callhome-server/global`` folder.
The name of this devices in NETCONF-topology will be in the ``IP-address:port``. 

Device-Specific Configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

To allow device and configuring name, the NETCONF Call-Home Server uses device provided
by an SSH server key (host key) to identify devices. The pairing of name and server key is
configured in ``/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices``.
This list is colloquially called a whitelist.

If the Call-Home Server finds the SSH host key in the whitelist, it continues to negotiate
a NETCONF connection over an SSH session. If the SSH host key is not found, the connection
between the Call-Home server and the device is dropped immediately. In either case, the device
that connects to the Call home server leaves a record of its presence in the operational store.
The following is an example of how to configure a device:

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

**URL:** ``/restconf/config/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices/device/example HTTP/1.1``

**Method:** ``PUT``

**Payload:**

.. code-block:: none

   {
    "device": {
    "unique-id": "example",
    "ssh-host-key": "AAAAB3NzaC1yc2EAAAADAQABAAABAQDHoH1jMjltOJnCt999uaSfc48ySutaD3ISJ9fSECe1Spdq9o9mxj0kBTTTq+2V8hPspuW75DNgN+V/rgJeoUewWwCAasRx9X4eTcRrJrwOQKzb5Fk+UKgQmenZ5uhLAefi2qXX/agFCtZi99vw+jHXZStfHm9TZCAf2zi+HIBzoVksSNJD0VvPo66EAvLn5qKWQD4AdpQQbKqXRf5/W8diPySbYdvOP2/7HFhDukW8yV/7ZtcywFUIu3gdXsrzwMnTqnATSLPPuckoi0V2jd8dQvEcu1DY+rRqmqu0tEkFBurlRZDf1yhNzq5xWY3OXcjgDGN+RxwuWQK3cRimcosH"
    }
   }

Configuring Device with Device-specific Credentials
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Call-Home Server also allows to configure credentials per device basis.
This is done by introducing credentials container into device-specific
configuration. Format is same as in global credentials.
Issue the following command to configure device with credentials:

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

* **URL:** ``/restconf/config/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices/device/example HTTP/1.1``

**Method:** ``PUT``

**Payload:**

.. code-block:: none

   {
    "device": {
     "unique-id": "example",
     "credentials": {
      "username": "example",
      "passwords": [ "password" ]
     },
     "ssh-host-key": "AAAAB3NzaC1yc2EAAAADAQABAAABAQDHoH1jMjltOJnCt999uaSfc48ySutaD3ISJ9fSECe1Spdq9o9mxj0kBTTTq+2V8hPspuW75DNgN+V/rgJeoUewWwCAasRx9X4eTcRrJrwOQKzb5Fk+UKgQmenZ5uhLAefi2qXX/agFCtZi99vw+jHXZStfHm9TZCAf2zi+HIBzoVksSNJD0VvPo66EAvLn5qKWQD4AdpQQbKqXRf5/W8diPySbYdvOP2/7HFhDukW8yV/7ZtcywFUIu3gdXsrzwMnTqnATSLPPuckoi0V2jd8dQvEcu1DY+rRqmqu0tEkFBurlRZDf1yhNzq5xWY3OXcjgDGN+RxwuWQK3cRimcosH"
    }
   }

Operational Status
------------------

Once an entry is made into the config side of “allowed-devices," the Call-Home Server will populate a
corresponding operational device that is the same as the config device but has an added status.
By default, this status is DISCONNECTED. Once a device calls home, this status will change to one of
the following:

.. list-table:: Operational Status
   :widths: 20 50
   :header-rows: 1

   * - **Status**
     - **Description**

   * - **CONNECTED**
     - Device is currently connected and the NETCONF mount is available for network management.
   * - **FAILED_AUTH_FAILURE**
     - The last attempted connection was unsuccessful because the Call-Home Server was unable to
       provide the acceptable credentials of the device. The device is also disconnected and not
       available for network management.
   * - **FAILED_NOT_ALLOWED**
     - The last attempted connection was unsuccessful because the device was not recognized as an
       acceptable device. The device is also disconnected and not available for network management.
   * - **FAILED**
     - The last attempted connection was unsuccessful for a reason other than not allowed to connect
       or incorrect client credentials. The device is also disconnected and not available for network management.
   * - **DISCONNECTED**
     - The device is currently disconnected.

Southbound Call-Home API
------------------------

The Call-Home Server listens for incoming TCP connections and assumes that the other side of the
connection is a device calling home via a NETCONF connection with SSH for management. By default,
the server uses port 6666, which can be configured via a blueprint configuration file.
The device must initiate the connection and the server will not try to re-establish the connection
when dropped. By requirement, the server cannot assume it has connectivity to the device due to NAT
or firewalls, among others.

.. meta::
   :robots: noindex, nofollow
