.. _netconf-mdsal:

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

