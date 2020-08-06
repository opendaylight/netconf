.. _netconf-mdsal:

NETCONF-Connector Configuration via MD-SAL
==========================================

A NETCONF-connector can be directly configured through MD-SAL via the
network-topology model via the NETCONF server for MD-SAL (port 2830).
Refer to the `MD-SAL <https://docs.opendaylight.org/projects/mdsal/en/latest/>`_
Web page for more information on MD-SAL.

To enable a NETCONF-connector configuration through MD-SAL:

#. Install either the ``odl-netconf-topology`` or ``odl-netconf-clustered-topology``
   feature.
#. Install the ``odl-restconf`` feature.

#. Issue the following command:

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

Configuring a NETCONF-Connector using MD-SAL
--------------------------------------------

There are four ways to create a new NETCONF-Connector using MD-SAL. In each case,
ensure that the device's name in ``<node-id>`` matches the last element of the URL.
To create a new NETCONF-Connector via MD-SAL, send one of the following PUT request to RESTCONF:

.. list-table::
   :widths: 20 60
   :header-rows: 1

   * - **Format**
     - **Description**

   * - **bierman02**
     - http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/new-netconf-device
   * - **rfc8040**
     - http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=new-netconf-device

You can also use the same ``body`` to create a NETCONF-connector with a POST "without" specifying
the node in the URL. For example:

.. list-table::
   :widths: 20 60
   :header-rows: 1

   * - **Format**
     - **Description**

   * - **bierman02**
     - http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf
   * - **rfc8040**
     - http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf

**Headers:**

* **Accept**: ``application/xml``
* **Content-Type**: ``application/xml``

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
-----------------------------------

Reconfiguring an existing connector is the same steps as :ref:`create-a-netconf`.
After reconfiguring a connector, the old connection disconnects and a new connector
with the new configuration is created. This is done with a ``PUT`` request since the
node already exists. In addition, a ``PATCH`` request can be used to change an existing
configuration. The URL would be the same as the ``PUT`` examples, while still using
JSON for the body.

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

Deleting an Existing Connector
------------------------------

To remove a NETCONF-connector, send a DELETE request to the same PUT request used when :ref:`create-a-netconf`:

.. list-table::
   :widths: 20 60
   :header-rows: 1

   * - **Format**
     - **Description**

   * - **bierman02**
     - http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/new-netconf-device
   * - **rfc8040**
     - http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=new-netconf-device

.. note:: No ``body`` is needed when deleting node/device.

Connect to a NETCONF 1.0 Device
-------------------------------

Some legacy NETCONF devices implement `RFC 4741 <https://tools.ietf.org/html/rfc4741>`_, which is not schema-based
and does not utilize YANG models internally. Therefore, it is difficult to communicate with these devices,
validate data, or what even know thier semantics of data. A NETCONF-connector can communicate with these devices,
but it must utilization of NETCONF mountpoints. Using RESTCONF with these types of devices is not also supported.
In addition, communicating with schema-less devices from application code is different.
To connect to schemai-less device, there is an optional configuration option in the ``netconf-node-topology model``
called **schemaless**. Set this option to ``true``.

