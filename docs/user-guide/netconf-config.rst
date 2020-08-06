.. _create-a-netconf:

NETCONF-Connector Configuration
===============================

Before creating a NETCONF-Connector, users must install a `netconf-connector` in Karaf.
Enter the following at the Karaf prompt to install a NETCONF connector:

.. code-block:: none

   feature:install odl-netconf-connector-all

The loopback mountpoint is automatically configured and activated.
Wait until the log displays the following:

.. code-block:: none

   RemoteDevice{controller-config}: NETCONF connector initialized successfully

Configuring a NETCONF-Connector
-------------------------------

Send the following request to RESTCONF to configure a new NETCONF-Connector:

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
^^^^^^^^^^^^^^^^^^^^

After creating the NETCONF-Connector, it attempts to connect to (or mount) to a NETCONF device at
127.0.0.1 and port 830. Send the following ``GET`` command to verify the config-subsystem’s
configuration datastore:

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config/yang-ext:mount/config:modules``

**Method:** GET

Find the Metadata
^^^^^^^^^^^^^^^^^

The response will contain the module for new-NETCONF-device. In addition, after the
NETCONF-Connector is created, it writes some useful metadata into the datastore of MD-SAL
under the network-topology subtree. Issue the following ``GET`` command to find the metadata:

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/operational/network-topology:network-topology/``

**Method:** GET

Information about connection status, device capabilities, etc. displays.

Connecting to a Device not Supporting NETCONF
---------------------------------------------

A NETCONF-Connector relies on `ietf-netconf-monitoring <https://tools.ietf.org/html/rfc6022>`_
support when connecting to a remote NETCONF device. This type of support allows a NETCONF-Connector
to list and download all YANG schemas that are used by a device. A NETCONF-Connector only communicates
with a device if it knows the set of used schemas (or at least a subset). However, some devices use
YANG models internally and do not support NETCONF monitoring. To allow a NETCONF-Connector to communicate
with these devices, users must side-load the necessary YANG models into OpenDaylight’s YANG model cache.
In general, there are two situations users might encounter:

* NETCONF device does not support ietf-netconf-monitoring but lists its YANG models as capabilities in
  HELLO message. This could be a device that internally uses only ietf-inet-types YANG model. In the
  HELLO message that is sent from this device, the following capability is reported:

  .. code-block:: none

     urn:ietf:params:xml:ns:yang:ietf-inet-types?module=ietf-inet-types&revision=2010-09-24

For this device, put the schema into the cache/schema folder, which is inside your Karaf distribution.
Call the file with YANG schema ``ietf-inet-types@2010-09-24.yang``. This is the required naming format
of the cache.

* NETCONF device does not support ietf-netconf-monitoring and it does NOT list its YANG models as capabilities
  in the HELLO message. In this case, there is no capability with ietf-inet-types in the HELLO message; thus,
  this type of device provides no information about its YANG schemas. Therefore, the user must properly
  configure NETCONF-Connector for this device.

  The NETCONF-Connector has an optional configuration attribute called ``yang-module-capabilities``.
  This attribute contains a list of “YANG module-based” capabilities, so users can set its configuration
  attribute to override the “yang-module-based” capabilities reported in the HELLO message of the device.
  To do this,  modify the NETCONF-Connector configuration by adding this to the XML. It must be added next
  to the address, port, username etc. configuration elements:

  .. code-block:: none

     <yang-module-capabilities xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
        <capability xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
         urn:ietf:params:xml:ns:yang:ietf-inet-types?module=ietf-inet-types&amp;revision=2010-09-24
        </capability>
     </yang-module-capabilities>

In addition, put this YANG schemas into the cache folder.

.. note:: When putting multiple capabilities, users must replicate the capability of the XML element inside
          yang-module-capability element. Capability element is modeled as a leaf-list. This configuration
          makes a remote device report usage of ietf-inet-types for a NETCONF-Connector.

Reconfiguring the NETCONF-Connector
-----------------------------------

Users can change the configuration of a running module even while the controller is running.
The following configuration example change the configuration of a new NETCONF-Connector
after it was created. Using one request, this example changes both the username and
password of the NETCONF-Connector. Since a ``PUT`` is a replace operation, the configuration
must be specified along with the new values for username and password. This should result in a
response with the NETCONF-Connector called ``new-netconf-device`` will be reconfigured to
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

Since a PUT is a replace operation, the entire configuration must be specified, as well as the new values
for username and password. This results in a 2xx response and an instance of NETCONF-Connector called
``new-netconf-device``, using the username of ``bob`` and password of ``passwd``.

Verify Configuration
^^^^^^^^^^^^^^^^^^^^

Execute the following to verify the previous configuration:

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

**URL:**

* **URL**: ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config/yang-ext:mount/config:modules/module/odl-sal-netconf-connector-cfg:sal-netconf-connector/new-netconf-device``

Deleting a Connector when a Controller is Running
-------------------------------------------------

Users can delete an instance of a module even while the controller is runner. In this case,
the module is removed, the NETCONF connection is dropped, and all resources are cleaned.
Issue the request to following URL to delete a NETCONF-Connector when the controller is running.

**Headers:**

- **Content-type:** ``application/xml``

- **Accept:** ``application/xml``

- **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config/yang-ext:mount/config:modules/module/odl-sal-netconf-connector-cfg:sal-netconf-connector/new-netconf-device``

**Method:** ``DELETE``

