===========================================
Netconf-connector configuration with MD-SAL
===========================================

It is also possible to configure new NETCONF connectors directly through
MD-SAL with the usage of the network-topology model. You can configure
new NETCONF connectors both through the NETCONF server for MD-SAL (port
2830) or RESTCONF. This guide focuses on RESTCONF.

.. tip::

    To enable NETCONF connector configuration through MD-SAL install
    either the ``odl-netconf-topology`` or
    ``odl-netconf-clustered-topology`` feature. We will explain the
    difference between these features later.

Preconditions
^^^^^^^^^^^^^

1. OpenDaylight is running

2. In Karaf, you must have the ``odl-netconf-topology`` or
   ``odl-netconf-clustered-topology`` feature installed.

3. Feature ``odl-restconf`` must be installed

4. Wait until log displays following entry:

   ::

       Successfully pushed configuration snapshot 02-netconf-topology.xml(odl-netconf-topology,odl-netconf-topology)

   or until

   ::

       GET http://localhost:8181/restconf/operational/network-topology:network-topology/topology/topology-netconf/

   returns a non-empty response, for example:

   ::

       <topology xmlns="urn:TBD:params:xml:ns:yang:network-topology">
         <topology-id>topology-netconf</topology-id>
       </topology>

.. _spawning-new-connector:

Spawning new NETCONF connectors
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

To create a new NETCONF connector you need to send the following PUT request
to RESTCONF:

.. list-table::
   :widths: 1 5

   * - bierman02
     - http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/new-netconf-device
   * - rfc8040
     - http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=new-netconf-device

You could use the same body to create the new  NETCONF connector with a POST
without specifying the node in the URL:

.. list-table::
   :widths: 1 5

   * - bierman02
     - http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf
   * - rfc8040
     - http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf

Headers:

-  Accept: application/xml

-  Content-Type: application/xml

Payload:

::

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

Note that the device name in <node-id> element must match the last
element of the restconf URL.

Reconfiguring an existing connector
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The steps to reconfigure an existing connector are exactly the same as
when spawning a new connector. The old connection will be disconnected
and a new connector with the new configuration will be created. This needs
to be done with a PUT request because the node already exists. A POST
request will fail for that reason.

Additionally, a PATCH request can be used to modify an existing
configuration. Currently, only yang-patch (`RFC-8072 <https://tools.ietf.org/html/rfc8072>`__)
is supported. The URL would be the same as the above PUT examples.
Using JSON for the body, the headers needed for the request would
be:

Headers:

-  Accept: application/yang.patch-status+json

-  Content-Type: application/yang.patch+json

Example JSON payload to modify the password entry:

::

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


Deleting an existing connector
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

To remove an already configured NETCONF connector you need to send a
DELETE request to the same PUT request URL that was used to create the
device:

.. list-table::
   :widths: 1 5

   * - bierman02
     - http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/new-netconf-device
   * - rfc8040
     - http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=new-netconf-device

.. note::

    No body is needed to delete the node/device

Connecting to a device supporting only NETCONF 1.0
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

OpenDaylight is schema-based distribution and heavily depends on YANG
models. However some legacy NETCONF devices are not schema-based and
implement just RFC 4741. This type of device does not utilize YANG
models internally and OpenDaylight does not know how to communicate
with such devices, how to validate data, or what the semantics of data
are.

NETCONF connector can communicate also with these devices, but the
trade-offs are worsened possibilities in utilization of NETCONF
mountpoints. Using RESTCONF with such devices is not suported. Also
communicating with schemaless devices from application code is slightly
different.

To connect to schemaless device, there is a optional configuration option
in netconf-node-topology model called schemaless. You have to set this
option to true.
