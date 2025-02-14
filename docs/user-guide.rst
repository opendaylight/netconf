.. _netconf-user-guide:

.. |ss| raw:: html

   <strike>

.. |se| raw:: html

   </strike>

NETCONF User Guide
==================

Overview
--------

NETCONF is an XML-based protocol used for configuration and monitoring
devices in the network. The base NETCONF protocol is described in
`RFC-6241 <https://www.rfc-editor.org/rfc/rfc6241>`__.

**NETCONF in OpenDaylight:.**

OpenDaylight supports the NETCONF protocol as a northbound server as
well as a southbound plugin. It also includes a set of test tools for
simulating NETCONF devices and clients.

Southbound (netconf-connector)
------------------------------

The NETCONF southbound plugin is capable of connecting to remote NETCONF
devices and exposing their configuration/operational datastores, RPCs
and notifications as MD-SAL mount points. These mount points allow
applications and remote users (over RESTCONF) to interact with the
mounted devices.

In terms of RFCs, the connector supports:

-  `RFC-6241 <https://www.rfc-editor.org/rfc/rfc6241>`__

-  `RFC-5277 <https://www.rfc-editor.org/rfc/rfc5277>`__

-  `RFC-6022 <https://www.rfc-editor.org/rfc/rfc6022>`__

-  `RFC-7895 <https://www.rfc-editor.org/rfc/rfc7895>`__

**Netconf-connector is fully model-driven (utilizing the YANG modeling
language) so in addition to the above RFCs, it supports any
data/RPC/notifications described by a YANG model that is implemented by
the device.**

.. tip::

    NETCONF southbound can be activated by installing
    ``odl-netconf-connector-all`` Karaf feature.

.. _netconf-connector:

Netconf-connector configuration
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

NETCONF connectors are configured directly through the usage of the
network-topology model. You can configure new NETCONF connectors both
through the NETCONF server for MD-SAL (port 2830) or RESTCONF. This guide
focuses on RESTCONF.

.. important::

    Since 2022.09 Chlorine there is only one RESTCONF endpoint:

    - | ``http://localhost:8181/rests`` is related to `RFC-8040 <https://www.rfc-editor.org/rfc/rfc8040>`__,
      | can be activated by installing ``odl-restconf-nb``
       Karaf feature.

    | Resources for configuration and operational datastores start
     ``/rests/data/``,
    | e. g. GET
     http://localhost:8181/rests/data/network-topology:network-topology
     with response of both datastores. It's allowed to use query
     parameters to distinguish between them.
    | e. g. GET
     http://localhost:8181/rests/data/network-topology:network-topology?content=config
     for configuration datastore
    | and GET
     http://localhost:8181/rests/data/network-topology:network-topology?content=nonconfig
     for operational datastore.

    | Also if a data node in the path expression is a YANG leaf-list or list
     node, the path segment has to be constructed by having leaf-list or
     list node name, followed by an "=" character, then followed by the
     leaf-list or list value. Any reserved characters must be
     percent-encoded.
    | e. g. GET
     http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf?content=config
     for retrieving data from configuration datastore for
     topology-netconf value of topology list.

Preconditions
^^^^^^^^^^^^^

1. OpenDaylight is running

2. In Karaf, you must have the ``odl-netconf-topology`` or
   ``odl-netconf-clustered-topology`` feature installed.

3. Feature ``odl-restconf-nb`` must be installed

Spawning new NETCONF connectors
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

To create a new NETCONF connector you need to send the following PUT request
to RESTCONF:

.. list-table::
   :widths: 1 5

   * - rfc8040
     - http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=new-netconf-device

You could use the same body to create the new  NETCONF connector with a POST
without specifying the node in the URL:

.. list-table::
   :widths: 1 5

   * - rfc8040
     - http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf

Payload for password authentication:

.. tabs::

   .. tab:: XML

      **Content-type:** ``application/xml``

      **Accept:** ``application/xml``

      **Authentication:** ``admin:admin``

      .. code-block:: xml

         <node xmlns="urn:TBD:params:xml:ns:yang:network-topology">
           <node-id>new-netconf-device</node-id>
             <netconf-node xmlns="urn:opendaylight:netconf-node-topology">
             <host xmlns="urn:opendaylight:netconf-node-topology">127.0.0.1</host>
             <port xmlns="urn:opendaylight:netconf-node-topology">17830</port>
             <login-password-unencrypted xmlns="urn:opendaylight:netconf-node-topology">
               <username xmlns="urn:opendaylight:netconf-node-topology">admin</username>
               <password xmlns="urn:opendaylight:netconf-node-topology">admin</password>
             </login-password-unencrypted>
             <tcp-only xmlns="urn:opendaylight:netconf-node-topology">false</tcp-only>
             <!-- non-mandatory fields with default values, you can safely remove these if you do not wish to override any of these values-->
             <reconnect-on-changed-schema xmlns="urn:opendaylight:netconf-node-topology">false</reconnect-on-changed-schema>
             <connection-timeout-millis xmlns="urn:opendaylight:netconf-node-topology">20000</connection-timeout-millis>
             <max-connection-attempts xmlns="urn:opendaylight:netconf-node-topology">0</max-connection-attempts>
             <min-backoff-millis xmlns="urn:opendaylight:netconf-node-topology">2000</min-backoff-millis>
             <max-backoff-millis xmlns="urn:opendaylight:netconf-node-topology">1800000</max-backoff-millis>
             <backoff-multiplier xmlns="urn:opendaylight:netconf-node-topology">1.5</backoff-multiplier>
             <!-- keepalive-delay set to 0 turns off keepalives-->
             <keepalive-delay xmlns="urn:opendaylight:netconf-node-topology">120</keepalive-delay>
           </netconf-node>
         </node>

   .. tab:: JSON

      **Content-type:** ``application/json``

      **Accept:** ``application/json``

      **Authentication:** ``admin:admin``

      .. code-block:: json

         {
             "node": [
                 {
                     "node-id": "new-netconf-device",
                     "netconf-node":{
                         "netconf-node-topology:port": 17830,
                         "netconf-node-topology:reconnect-on-changed-schema": false,
                         "netconf-node-topology:connection-timeout-millis": 20000,
                         "netconf-node-topology:tcp-only": false,
                         "netconf-node-topology:max-connection-attempts": 0,
                         "netconf-node-topology:login-password-unencrypted": {
                            "netconf-node-topology:username": "admin",
                            "netconf-node-topology:password": "admin"
                         },
                         "netconf-node-topology:host": "127.0.0.1",
                         "netconf-node-topology:min-backoff-millis": 2000,
                         "netconf-node-topology:max-backoff-millis": 1800000,
                         "netconf-node-topology:backoff-multiplier": 1.5,
                         "netconf-node-topology:keepalive-delay": 120
                     }
                 }
             ]
         }

.. note::

    You have the option to use the 'login-password' configuration for authentication as shown below:

    .. code-block:: json

        "login-password": {
            "netconf-node-topology:username": "netconf",
            "netconf-node-topology:password": "c5R3aLBss7J8T2VC3pEeAQ=="
        }

    In OpenDaylight's configuration, the AAAEncryptionServiceImpl generates a new encryption key with
    each application build. You can use this method if you have access to the current encryption key.
    Additionally, it is important to ensure that the entire password is encoded in base64 format and
    that its length is a multiple of 16 bytes for successful authentication.

There is also option of using key-based authentication instead
of password. First we need to create key in datastore. How to do
this is described in the `Netconf-keystore configuration`_ section,
where you find the necessary RPC to add keystore entries.

Payload for key-based authentication via SSH:

.. tabs::

   .. tab:: XML

      **Content-type:** ``application/xml``

      **Accept:** ``application/xml``

      **Authentication:** ``admin:admin``

      .. code-block:: xml

         <node xmlns="urn:TBD:params:xml:ns:yang:network-topology">
           <node-id>new-netconf-device</node-id>
           <netconf-node xmlns="urn:opendaylight:netconf-node-topology">
             <host xmlns="urn:opendaylight:netconf-node-topology">127.0.0.1</host>
             <port xmlns="urn:opendaylight:netconf-node-topology">17830</port>
             <key-based xmlns="urn:opendaylight:netconf-node-topology">
               <username xmlns="urn:opendaylight:netconf-node-topology">admin</username>
               <key-id xmlns="urn:opendaylight:netconf-node-topology">key-id</password>
             </key-based>
             <tcp-only xmlns="urn:opendaylight:netconf-node-topology">false</tcp-only>
             <!-- non-mandatory fields with default values, you can safely remove these if you do not wish to override any of these values-->
             <reconnect-on-changed-schema xmlns="urn:opendaylight:netconf-node-topology">false</reconnect-on-changed-schema>
             <connection-timeout-millis xmlns="urn:opendaylight:netconf-node-topology">20000</connection-timeout-millis>
             <max-connection-attempts xmlns="urn:opendaylight:netconf-node-topology">0</max-connection-attempts>
             <min-backoff-millis xmlns="urn:opendaylight:netconf-node-topology">2000</min-backoff-millis>
             <max-backoff-millis xmlns="urn:opendaylight:netconf-node-topology">1800000</max-backoff-millis>
             <backoff-multiplier xmlns="urn:opendaylight:netconf-node-topology">1.5</backoff-multiplier>
             <!-- keepalive-delay set to 0 turns off keepalives-->
             <keepalive-delay xmlns="urn:opendaylight:netconf-node-topology">120</keepalive-delay>
           </netconf-node>
         </node>

   .. tab:: JSON

      **Content-type:** ``application/json``

      **Accept:** ``application/json``

      **Authentication:** ``admin:admin``

      .. code-block:: json

         {
             "node": [
                 {
                     "node-id": "new-netconf-device",
                     "netconf-node":{
                         "netconf-node-topology:port": 17830,
                         "netconf-node-topology:reconnect-on-changed-schema": false,
                         "netconf-node-topology:connection-timeout-millis": 20000,
                         "netconf-node-topology:tcp-only": false,
                         "netconf-node-topology:max-connection-attempts": 0,
                         "netconf-node-topology:key-based": {
                            "netconf-node-topology:username": "admin",
                            "netconf-node-topology:key-id": "key-id"
                         },
                         "netconf-node-topology:host": "127.0.0.1",
                         "netconf-node-topology:min-backoff-millis": 2000,
                         "netconf-node-topology:max-backoff-millis": 1800000,
                         "netconf-node-topology:backoff-multiplier": 1.5,
                         "netconf-node-topology:keepalive-delay": 120
                     }
                 }
             ]
         }

Connecting via TLS protocol is similar to SSH. First setup keystore
by using three RPCs from `Netconf-keystore configuration`_
to add a client private key, associate a private key with a client and CA
certificate chain, and add a list of trusted CA and server certificates.
Only after that we can process and create a new NETCONF connector you need
to send the following PUT request.

Payload for key-based authentication via TLS:

.. tabs::

   .. tab:: XML

      **Content-type:** ``application/xml``

      **Accept:** ``application/xml``

      **Authentication:** ``admin:admin``

      .. code-block:: xml

         <node xmlns="urn:TBD:params:xml:ns:yang:network-topology">
           <node-id>new-netconf-device</node-id>
           <netconf-node xmlns="urn:opendaylight:netconf-node-topology">
             <host xmlns="urn:opendaylight:netconf-node-topology">127.0.0.1</host>
             <port xmlns="urn:opendaylight:netconf-node-topology">17830</port>
             <key-based xmlns="urn:opendaylight:netconf-node-topology">
               <username xmlns="urn:opendaylight:netconf-node-topology">admin</username>
               <key-id xmlns="urn:opendaylight:netconf-node-topology">key-id</key-id>
             </key-based>
             <tcp-only xmlns="urn:opendaylight:netconf-node-topology">false</tcp-only>
             <!-- non-mandatory fields with default values, you can safely remove these if you do not wish to override any of these values-->
             <reconnect-on-changed-schema xmlns="urn:opendaylight:netconf-node-topology">false</reconnect-on-changed-schema>
             <connection-timeout-millis xmlns="urn:opendaylight:netconf-node-topology">20000</connection-timeout-millis>
             <max-connection-attempts xmlns="urn:opendaylight:netconf-node-topology">0</max-connection-attempts>
             <min-backoff-millis xmlns="urn:opendaylight:netconf-node-topology">2000</min-backoff-millis>
             <max-backoff-millis xmlns="urn:opendaylight:netconf-node-topology">1800000</max-backoff-millis>
             <backoff-multiplier xmlns="urn:opendaylight:netconf-node-topology">1.5</backoff-multiplier>
             <!-- keepalive-delay set to 0 turns off keepalives-->
             <keepalive-delay xmlns="urn:opendaylight:netconf-node-topology">120</keepalive-delay>
             <protocol xmlns="urn:opendaylight:netconf-node-topology">
               <name xmlns="urn:opendaylight:netconf-node-topology">TLS</name>
               <key-id xmlns="urn:opendaylight:netconf-node-topology">key-id1</key-id>
               <key-id xmlns="urn:opendaylight:netconf-node-topology">key-id2</key-id>
             </protocol>
           </netconf-node>
         </node>

   .. tab:: JSON

      **Content-type:** ``application/json``

      **Accept:** ``application/json``

      **Authentication:** ``admin:admin``

      .. code-block:: json

         {
             "node": [
                 {
                     "node-id": "new-netconf-device",
                     "netconf-node":{
                         "netconf-node-topology:port": 17830,
                         "netconf-node-topology:reconnect-on-changed-schema": false,
                         "netconf-node-topology:connection-timeout-millis": 20000,
                         "netconf-node-topology:tcp-only": false,
                         "netconf-node-topology:max-connection-attempts": 0,
                         "netconf-node-topology:key-based": {
                            "netconf-node-topology:username": "admin",
                            "netconf-node-topology:key-id": "key-id"
                         },
                         "netconf-node-topology:host": "127.0.0.1",
                         "netconf-node-topology:min-backoff-millis": 2000,
                         "netconf-node-topology:max-backoff-millis": 1800000,
                         "netconf-node-topology:backoff-multiplier": 1.5,
                         "netconf-node-topology:keepalive-delay": 120,
                         "protocol": {
                            "name": "TLS",
                            "key-id": ["key-id1", "key-id2"]
                         }
                     }
                 }
             ]
         }


Note that the device name in <node-id> element must match the last
element of the restconf URL. <key-id> inside protocol node is optional and
used only in case of TLS protocol to specify set of keys that will be used
for TLS connection. If you don't specify this set, then all keys inside of
the keystore will be used. Also <key-id> for key based credentials and
<key-id> inside protocol node may or may not refer to the same key.

Reconfiguring an existing connector
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The steps to reconfigure an existing connector are exactly the same as
when spawning a new connector. The old connection will be disconnected
and a new connector with the new configuration will be created. This needs
to be done with a PUT request because the node already exists. A POST
request will fail for that reason.

Additionally, a PATCH request can be used to modify an existing
configuration. Currently, only yang-patch (`RFC-8072 <https://www.rfc-editor.org/rfc/rfc8072>`__)
is supported. The URL would be the same as the above PUT examples.
Using JSON for the body, the headers needed for the request would
be:

Headers:

-  Accept: application/yang-data+json

-  Content-Type: application/yang-patch+json

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
                 "netconf-node":{
                    "netconf-node-topology:password" : "newpassword"
                 }
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

   * - rfc8040
     - http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=new-netconf-device

.. note::

    No body is needed to delete the node/device

Connecting to a device not supporting NETCONF monitoring
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The netconf-connector in OpenDaylight relies on ietf-netconf-monitoring
support when connecting to remote NETCONF device. The
ietf-netconf-monitoring support allows netconf-connector to list and
download all YANG schemas that are used by the device. NETCONF connector
can only communicate with a device if it knows the set of used schemas
(or at least a subset). However, some devices use YANG models internally
but do not support NETCONF monitoring. Netconf-connector can also
communicate with these devices, but you have to side load the necessary
yang models into OpenDaylight’s YANG model cache for netconf-connector.
In general there are 2 situations you might encounter:

**1. NETCONF device does not support ietf-netconf-monitoring but it does
list all its YANG models as capabilities in HELLO message**

This could be a device that internally uses only ietf-inet-types YANG
model with revision 2010-09-24. In the HELLO message that is sent from
this device there is this capability reported:

::

    urn:ietf:params:xml:ns:yang:ietf-inet-types?module=ietf-inet-types&revision=2010-09-24

**For such devices you only need to put the schema into folder
cache/schema inside your Karaf distribution.**

.. important::

    The file with YANG schema for ietf-inet-types has to be called
    ietf-inet-types@2010-09-24.yang. It is the required naming format of
    the cache.

**2. NETCONF device does not support ietf-netconf-monitoring and it does
NOT list its YANG models as capabilities in HELLO message**

Compared to device that lists its YANG models in HELLO message, in this
case there would be no capability with ietf-inet-types in the HELLO
message. This type of device basically provides no information about the
YANG schemas it uses so its up to the user of OpenDaylight to properly
configure netconf-connector for this device.

Netconf-connector has an optional configuration attribute called
yang-module-capabilities and this attribute can contain a list of "YANG
module based" capabilities. So by setting this configuration attribute,
it is possible to override the "yang-module-based" capabilities reported
in HELLO message of the device. To do this, we need to modify the
configuration of netconf-connector like in the example below:

.. tabs::

   .. tab:: XML

      **Content-type:** ``application/xml``

      **Accept:** ``application/xml``

      **Authentication:** ``admin:admin``

      .. code-block:: xml

         <node xmlns="urn:TBD:params:xml:ns:yang:network-topology">
           <node-id>r5</node-id>
           <netconf-node xmlns="urn:opendaylight:netconf-node-topology">
             <host xmlns="urn:opendaylight:netconf-node-topology">127.0.0.1</host>
             <port xmlns="urn:opendaylight:netconf-node-topology">8305</port>
             <login-password-unencrypted xmlns="urn:opendaylight:netconf-node-topology">
               <username xmlns="urn:opendaylight:netconf-node-topology">root</username>
               <password xmlns="urn:opendaylight:netconf-node-topology">root</password>
             </login-password-unencrypted>
             <tcp-only xmlns="urn:opendaylight:netconf-node-topology">false</tcp-only>
             <keepalive-delay xmlns="urn:opendaylight:netconf-node-topology">30</keepalive-delay>
             <yang-module-capabilities xmlns="urn:opendaylight:netconf-node-topology">
               <override>true</override>
               <capability xmlns="urn:opendaylight:netconf-node-topology">
                 urn:ietf:params:xml:ns:yang:ietf-inet-types?module=ietf-inet-types&amp;revision=2013-07-15
               </capability>
             </yang-module-capabilities>
           </netconf-node>
         </node>

   .. tab:: JSON

      **Content-type:** ``application/json``

      **Accept:** ``application/json``

      **Authentication:** ``admin:admin``

      .. code-block:: json

         {
             "node": [
                 {
                     "node-id": "device",
                     "netconf-node":{
                         "netconf-node-topology:host": "127.0.0.1",
                         "netconf-node-topology:login-password-unencrypted": {
                            "netconf-node-topology:password": "root",
                            "netconf-node-topology:username": "root"
                         },
                         "netconf-node-topology:yang-module-capabilities": {
                             "override": true,
                             "capability": [
                                 "urn:ietf:params:xml:ns:yang:ietf-inet-types?module=ietf-inet-types&revision=2013-07-15"
                             ]
                         },
                         "netconf-node-topology:port": 8305,
                         "netconf-node-topology:tcp-only": false,
                         "netconf-node-topology:keepalive-delay": 30
                     }
                 }
             ]
         }

**Remember to also put the YANG schemas into the cache folder.**

.. note::

    For putting multiple capabilities, you just need to replicate the
    capability element inside yang-module-capability element.
    Capability element is modeled as a leaf-list. With this
    configuration, we would make the remote device report usage of
    ietf-inet-types in the eyes of netconf-connector.

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
mountpoints. Using RESTCONF with such devices is not supported. Also
communicating with schemaless devices from application code is slightly
different.

To connect to schemaless device, there is a optional configuration option
in netconf-node-topology model called schemaless. You have to set this
option to true.

Clustered NETCONF connector
~~~~~~~~~~~~~~~~~~~~~~~~~~~

To spawn NETCONF connectors that are cluster-aware you need to install
the ``odl-netconf-clustered-topology`` karaf feature.

.. warning::

    The ``odl-netconf-topology`` and ``odl-netconf-clustered-topology``
    features are considered **INCOMPATIBLE**. They both manage the same
    space in the datastore and would issue conflicting writes if
    installed together.

Configuration of clustered NETCONF connectors works the same as the
configuration through the topology model in the previous section.

When a new clustered connector is configured the configuration gets
distributed among the member nodes and a NETCONF connector is spawned on
each node. From these nodes a master is chosen which handles the schema
download from the device and all the communication with the device. You
will be able to read/write to/from the device from all slave nodes due
to the proxy data brokers implemented.

You can use the ``odl-netconf-clustered-topology`` feature in a single
node scenario as well but the code that uses akka will be used, so for a
scenario where only a single node is used, ``odl-netconf-topology``
might be preferred.

Netconf-connector utilization
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Once the connector is up and running, users can utilize the new mount
point instance. By using RESTCONF or from their application code. This
chapter deals with using RESTCONF and more information for app
developers can be found in the developers guide or in the official
tutorial application **ncmount** that can be found in the coretutorials
project:

-  https://github.com/opendaylight/coretutorials/tree/master/ncmount

Reading data from the device
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Just invoke (no body needed):

GET
http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=new-netconf-device/yang-ext:mount?content=nonconfig

This will return the entire content of operation datastore from the
device. To view just the configuration datastore, change **nonconfig**
in this URL to **config**.

Writing configuration data to the device
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

In general, you cannot simply write any data you want to the device. The
data have to conform to the YANG models implemented by the device. In
this example we are adding a new interface-configuration to the mounted
device (assuming the device supports Cisco-IOS-XR-ifmgr-cfg YANG model).
In fact this request comes from the tutorial dedicated to the
**ncmount** tutorial app.

POST
http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=new-netconf-device/yang-ext:mount/Cisco-IOS-XR-ifmgr-cfg:interface-configurations

::

    <interface-configuration xmlns="http://cisco.com/ns/yang/Cisco-IOS-XR-ifmgr-cfg">
        <active>act</active>
        <interface-name>mpls</interface-name>
        <description>Interface description</description>
        <bandwidth>32</bandwidth>
        <link-status></link-status>
    </interface-configuration>

Should return 200 response code with no body.

.. tip::

    This call is transformed into a couple of NETCONF RPCs. Resulting
    NETCONF RPCs that go directly to the device can be found in the
    OpenDaylight logs after invoking ``log:set TRACE
    org.opendaylight.controller.sal.connect.netconf`` in the Karaf
    shell. Seeing the NETCONF RPCs might help with debugging.

This request is very similar to the one where we spawned a new netconf
device. That’s because we used the loopback netconf-connector to write
configuration data into config-subsystem datastore and config-subsystem
picked it up from there.

Invoking custom RPC
^^^^^^^^^^^^^^^^^^^

Devices can implement any additional RPC and as long as it provides YANG
models for it, it can be invoked from OpenDaylight. Following example
shows how to invoke the get-schema RPC (get-schema is quite common among
netconf devices). Invoke:

POST
http://localhost:8181/rests/operations/network-topology:network-topology/topology=topology-netconf/node=new-netconf-device/yang-ext:mount/ietf-netconf-monitoring:get-schema

::

    <input xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
      <identifier>ietf-yang-types</identifier>
      <version>2013-07-15</version>
    </input>

This call should fetch the source for ietf-yang-types YANG model from
the mounted device.

Receiving Netconf Device Notifications on a http client
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Devices emit netconf alarms and notifications in certain situations, which can demand
attention from Device Administration. The notifications are received as Netconf messages on an
active Netconf session.

Opendaylight provides the way to stream the device notifications over a http session.

- Step 1: Mount the device (assume node name is test_device)

- Step 2: Wait for the device to be connected.

- Step 3: Create the Subscription for notification on the active session.

 .. code-block::

    POST
    http://localhost:8181/rests/operations/network-topology:network-topology/topology=topology-netconf/node=test_device/yang-ext:mount/notifications:create-subscription
    Content-Type: application/json
    Accept: application/json

 .. code-block:: json

    {
      "input": {
        "stream": "NETCONF"
       }
    }

- Step 4: Create the http Stream for the events.

.. code-block::

    POST
    http://localhost:8181/rests/operations/odl-device-notification:subscribe-device-notification
    Content-Type: application/json
    Accept: application/json

.. code-block:: json

    {
      "input": {
         "path":"/network-topology:network-topology/topology[topology-id='topology-netconf']/node[node-id='test_device']"
      }
    }

The response contains the stream name for reading the notifications.

.. code-block:: json

    {
       "odl-device-notification:output": {
            "stream-name": "urn:uuid:91e630ec-1324-4f57-bae3-0925b6d11ffd"
        }
    }

- Step 5: To receive notifications send GET request to url as follows:

.. code-block::

    http://localhost:8181/rests/streams/{encoding}/{stream-name}

{stream-name} - being **stream-name** received in previous step

{encoding} - being desired encoding to be received, either "xml" or "json"

The request for xml encoding and **stream-name** from previous example would look like this:

.. code-block::

    GET
    http://localhost:8181/rests/streams/xml/urn:uuid:91e630ec-1324-4f57-bae3-0925b6d11ffd
    Accept: text/event-stream


.. code-block:: xml

    : ping

    : ping

    : ping

    : ping

    : ping

    data: <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0"><eventTime>2022-06-17T07:01:08.60228Z</eventTime><netconf-session-start xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-notifications"><username>root</username><source-host>127.0.0.1</source-host><session-id>2</session-id></netconf-session-start></notification>

    data: <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0"><eventTime>2022-06-17T07:01:12.458258Z</eventTime><netconf-session-end xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-notifications"><username>root</username><source-host>127.0.0.1</source-host><termination-reason>closed</termination-reason><session-id>2</session-id></netconf-session-end></notification>

Change event notification subscription tutorial
-----------------------------------------------

Subscribing to data change notifications makes it possible to obtain
notifications about data manipulation (insert, change, delete) which are
done on any specified **path** of any specified **datastore** with
specific **scope**. In following examples *{odlAddress}* is address of
server where ODL is running and *{odlPort}* is port on which
OpenDaylight is running. OpenDaylight offers Server-Sent Events (SSE) method for receiving notifications.

SSE notifications subscription process
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In this section we will learn what steps need to be taken in order to
successfully subscribe to data change event notifications.

Create stream
^^^^^^^^^^^^^

In order to use event notifications you first need to call RPC that
creates notification stream that you can later listen to. You need to
provide three parameters to this RPC:

-  **path**: data store path that you plan to listen to. You can
   register listener on containers, lists and leaves.

-  **datastore**: data store type. *OPERATIONAL* or *CONFIGURATION*.

-  **scope**: Represents scope of data change. Possible options are:

   -  BASE: only changes directly to the data tree node specified in the
      path will be reported

   -  ONE: changes to the node and to direct child nodes will be
      reported

   -  SUBTREE: changes anywhere in the subtree starting at the node will
      be reported

The RPC to create the stream can be invoked via RESTCONF like this:

::

    OPERATION: POST
    URI:  http://{odlAddress}:{odlPort}/rests/operations/sal-remote:create-data-change-event-subscription
    HEADER: Content-Type=application/json
            Accept=application/json

.. code-block:: json

       {
           "input": {
               "path": "/toaster:toaster/toaster:toasterStatus",
               "sal-remote-augment:datastore": "OPERATIONAL",
               "sal-remote-augment:scope": "ONE"
           }
       }

The response should look something like this:

.. code-block:: json

    {
        "sal-remote:output": {
            "stream-name": "urn:uuid:b3db417c-0305-473d-b6c8-2da01c543171"
        }
    }

**stream-name** is important because you will need to use it when you
subscribe to the stream in the next step.

.. note::

    Internally, this will create a new listener for *stream-name* if it
    did not already exist.

Subscribe to stream
^^^^^^^^^^^^^^^^^^^

In order to subscribe to stream and obtain SSE location you need
to call *GET* on your stream path. The URI should generally be
`http://{odlAddress}:{odlPort}/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream={streamName}`,
where *{streamName}* is the *stream-name* parameter contained in
response from *create-data-change-event-subscription* RPC from the
previous step.

::

   OPERATION: GET
   URI: http://{odlAddress}:{odlPort}/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream=urn:uuid:b3db417c-0305-473d-b6c8-2da01c543171

The response should look something like this:

.. code-block:: json

    {
        "ietf-restconf-monitoring:stream": [
            {
                "name": "urn:uuid:b3db417c-0305-473d-b6c8-2da01c543171",
                "access": [
                    {
                        "encoding": "json",
                        "location": "http://127.0.0.1:8181/rests/streams/json/urn:uuid:b3db417c-0305-473d-b6c8-2da01c543171"
                    },
                    {
                        "encoding": "xml",
                        "location": "http://127.0.0.1:8181/rests/streams/xml/urn:uuid:b3db417c-0305-473d-b6c8-2da01c543171"
                    }
                ],
                "description": "Events occuring in OPERATIONAL datastore under /toaster:toaster/toasterStatus"
            }
        ]
    }

.. note::

    During this phase there is an internal check for to see if a
    listener for the *stream-name* from the URI exists. If not,
    new listener is registered with the DOM data broker.

Receive notifications
^^^^^^^^^^^^^^^^^^^^^

Once you got SSE location you can now connect to it and
start receiving data change events. You can choose which encoding to use.
The request should look something like this:

::

    curl -v -X GET  http://localhost:8181/rests/streams/json/urn:uuid:b3db417c-0305-473d-b6c8-2da01c543171  -H "Content-Type: text/event-stream" -H "Authorization: Basic YWRtaW46YWRtaW4="

The subscription call may be modified with the following query parameters defined in the RESTCONF RFC:

-  `filter <https://www.rfc-editor.org/rfc/rfc8040#section-4.8.4>`__

-  `start-time <https://www.rfc-editor.org/rfc/rfc8040#section-4.8.7>`__

-  `end-time <https://www.rfc-editor.org/rfc/rfc8040#section-4.8.8>`__

In addition, the following ODL extension query parameter is supported:

:odl-leaf-nodes-only:
  If this parameter is set to "true", create and update notifications will only
  contain the leaf nodes modified instead of the entire subscription subtree.
  This can help in reducing the size of the notifications.

:odl-skip-notification-data:
  If this parameter is set to "true", create and update notifications will only
  contain modified leaf nodes without data.
  This can help in reducing the size of the notifications.

Subscription to YANG Notifications tutorial
-------------------------------------------

OpenDaylight's NETCONF implementation enables dynamic subscription management to receive notifications
about specific events or changes in the data tree. `RFC 8639 <https://www.rfc-editor.org/rfc/rfc8639>`__ provides
a standardized method for establishing, modifying, and terminating these subscriptions,
ensuring interoperability between different NETCONF clients and servers.

Establishing a Subscription
~~~~~~~~~~~~~~~~~~~~~~~~~~~

To establish a new subscription, use the establish-subscription RPC as defined
in `RFC-8650 <https://www.rfc-editor.org/rfc/rfc8650#section-a.1.1-1>`__.

POST request to:

.. code-block::

    http://localhost:8182/restconf/operations/ietf-subscribed-notifications:establish-subscription

.. tabs::

   .. tab:: XML

      **Content-type:** ``application/xml``

      **Accept:** ``application/xml``

      **Authentication:** ``admin:admin``

      .. code-block:: xml

          <input xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
            <stream>NETCONF</stream>
            <encoding>encode-xml</encoding>
            <stream-subtree-filter></ietf-vrrp:vrrp-protocol-error-event></stream-subtree-filter>
          </input>

   .. tab:: JSON

       **Content-type:** ``application/json``

       **Accept:** ``application/json``

       **Authentication:** ``admin:admin``

       .. code-block:: json

           {
              "ietf-subscribed-notifications:input": {
                  "stream": "NETCONF",
                  "encoding": "json",
                  "stream-subtree-filter": {
                      "ietf-vrrp:vrrp-protocol-error-event" : {}
                  },
              }
           }

Upon successful establishment, the server returns a subscription ID:

.. code-block:: json

    {
      "output": {
        "subscription-id": "2147483648"
      }
    }

Subtree Filtering in Subscriptions
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Subscriptions can include filtering capabilities such as subtree filters to ensure that only
relevant notifications are sent to the client.
This filtering mechanism follows the subtree filter specification from `RFC-6241 <https://www.rfc-editor.org/rfc/rfc6241#section-6.4>`__.,
which provides flexibility in selecting specific subtrees or elements.
A subtree filter operates on the data structure of the event records within the stream.
By specifying containment nodes and selection nodes, you can fine-tune the notifications to include only the desired information.
The examples of filtering provided in this section are illustrative only and are not implemented by OpenDaylight.

Examples of Subtree Filtering
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Select the Entire <users> Subtree
'''''''''''''''''''''''''''''''''

This filter selects all data under the <users> subtree.
Use this when you want to include everything within a specific subtree in the notifications.

.. tabs::

   .. tab:: XML

      **Content-type:** ``application/xml``

      **Accept:** ``application/xml``

      **Authentication:** ``admin:admin``

      .. code-block:: xml

          <input xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
            <stream>NETCONF</stream>
            <encoding>xml</encoding>
            <stream-subtree-filter>
                <users/>
            </stream-subtree-filter>
          </input>

   .. tab:: JSON

       **Content-type:** ``application/json``

       **Accept:** ``application/json``

       **Authentication:** ``admin:admin``

       .. code-block:: json

           {
              "ietf-subscribed-notifications:input": {
                  "stream": "NETCONF",
                  "encoding": "encode-json",
                  "stream-subtree-filter": {
                      "users" : {}
                  },
              }
           }

Select Specific Elements within the <users> Subtree
'''''''''''''''''''''''''''''''''''''''''''''''''''

This filter retrieves only the <name> elements from each <user> within the <users> subtree.

.. tabs::

   .. tab:: XML

      **Content-type:** ``application/xml``

      **Accept:** ``application/xml``

      **Authentication:** ``admin:admin``

      .. code-block:: xml

          <input xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
            <stream>NETCONF</stream>
            <encoding>encode-xml</encoding>
            <stream-subtree-filter>
              <users>
                <user>
                  <name/>
                </user>
              </users>
            </stream-subtree-filter>
          </input>

   .. tab:: JSON

       **Content-type:** ``application/json``

       **Accept:** ``application/json``

       **Authentication:** ``admin:admin``

       .. code-block:: json

           {
             "ietf-subscribed-notifications:input": {
               "stream": "NETCONF",
               "encoding": "encode-json",
               "stream-subtree-filter": {
                 "users": {
                   "user": {
                     "name": {}
                   }
                 }
               }
             }
           }

Listening to Notifications
~~~~~~~~~~~~~~~~~~~~~~~~~~

Once a subscription is established, you can start listening to the stream of notifications.
To listen to the notifications for an established subscription, use the following RESTCONF URL format:

.. code-block::

    GET
    http://localhost:8182/subscriptions/{subscription-id}
    Accept: text/event-stream

Replace {subscription-id} with the ID returned in the establish-subscription RPC response.

Modifying a Subscription
~~~~~~~~~~~~~~~~~~~~~~~~

Modify an existing subscription to adjust parameters such as filtering XPath or encoding.

Use the modify-subscription RPC to change parameters on an active subscription:

POST request to:

.. code-block::

    http://localhost:8182/restconf/operations/ietf-subscribed-notifications:modify-subscription

.. tabs::

   .. tab:: XML

      **Content-type:** ``application/xml``

      **Accept:** ``application/xml``

      **Authentication:** ``admin:admin``

      .. code-block:: xml

          <input xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
            <subscription-id>2147483648</subscription-id>
            <stream-subtree-filter></ietf-vrrp:vrrp-protocol-error-event></stream-subtree-filter>
            <encoding>encode-xml</encoding>
          </input>

   .. tab:: JSON

       **Content-type:** ``application/json``

       **Accept:** ``application/json``

       **Authentication:** ``admin:admin``

       .. code-block:: json

           {
               "ietf-subscribed-notifications:input": {
                 "subscription-id": "2147483648",
                 "stream-subtree-filter": {
                     "/ietf-vrrp:vrrp-protocol-error-event" : {}
                 },
                 "encoding": "encode-xml"
               }
           }

Terminating a Subscription
~~~~~~~~~~~~~~~~~~~~~~~~~~
To terminate a subscription, you can either delete it (delete-subscription) or forcibly kill it (kill-subscription).

Using delete-subscription
^^^^^^^^^^^^^^^^^^^^^^^^^

This RPC allows a subscriber to delete a subscription that was previously created by the same subscriber who used the
'establish-subscription' RPC.

POST request to:

.. code-block::

    http://localhost:8182/restconf/operations/ietf-subscribed-notifications:modify-subscription

.. tabs::

   .. tab:: XML

      **Content-type:** ``application/xml``

      **Accept:** ``application/xml``

      **Authentication:** ``admin:admin``

      .. code-block:: xml

          <input xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
            <subscription-id>2147483648</subscription-id>
          </input>

   .. tab:: JSON

       **Content-type:** ``application/json``

       **Accept:** ``application/json``

       **Authentication:** ``admin:admin``

       .. code-block:: json

           {
               "ietf-subscribed-notifications:input": {
                 "subscription-id": "2147483648"
               }
           }

Using kill-subscription
^^^^^^^^^^^^^^^^^^^^^^^

The kill-subscription RPC forcibly removes a subscription, which is useful if
the subscription encounters persistent issues.

POST request to:

.. code-block::

    http://localhost:8182/restconf/operations/ietf-subscribed-notifications:kill-subscription

.. tabs::

   .. tab:: XML

      **Content-type:** ``application/xml``

      **Accept:** ``application/xml``

      **Authentication:** ``admin:admin``

      .. code-block:: xml

          <input xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
            <subscription-id>2147483648</subscription-id>
          </input>

   .. tab:: JSON

       **Content-type:** ``application/json``

       **Accept:** ``application/json``

       **Authentication:** ``admin:admin``

       .. code-block:: json

           {
               "ietf-subscribed-notifications:input": {
                 "subscription-id": "2147483648"
               }
           }

Netconf-keystore configuration
------------------------------

To configure the netconf-keystore, you need to send three RPCs
to add a client private key, associate a private key with a client and CA
certificate chain, and add a list of trusted CA and server certificates.

.. note::

    The netconf-keystore is a feature that is not enabled by default.
    To enable it, you need to install the ``odl-netconf-keystore`` feature.
    If you have already installed the ``odl-netconf-topology`` feature to connect
    to a device, you do not need to install the ``odl-netconf-keystore`` feature.

*Adding a client private key credential to the netconf-keystore*

.. code-block::

    POST HTTP/1.1
    /rests/operations/netconf-keystore:add-keystore-entry
    Content-Type: application/json
    Accept: application/json

.. code-block:: json

  {
    "input": {
      "key-credential": [
        {
          "key-id": "example-client-key-id",
          "private-key": "PEM-format-private-key",
          "passphrase": "passphrase"
        }
      ]
    }
  }

*Associate a private key with a client and CA certificates chain*

.. code-block::

    POST HTTP/1.1
    /rests/operations/netconf-keystore:add-private-key
    Content-Type: application/json
    Accept: application/json

.. code-block:: json

  {
    "input": {
      "private-key": [
        {
          "name": "example-client-key-id",
          "data": "key-data",
          "certificate-chain": [
            "certificate-data"
          ]
        }
      ]
    }
  }

*Add a list of trusted CA and server certificates*

.. code-block::

    POST HTTP/1.1
    /rests/operations/netconf-keystore:add-trusted-certificate
    Content-Type: application/json
    Accept: application/json

.. code-block:: json

  {
    "input": {
      "trusted-certificate": [
        {
          "name": "example-ca-certificate",
          "certificate": "ca-certificate-data"
        },
        {
          "name": "example-server-certificate",
          "certificate": "server-certificate-data"
        }
      ]
    }
  }

.. note::

    All keys and certificates must be in PEM format with valid data,
    which means that the data must be base64 encoded and wrapped in the
    appropriate PEM header and footer.

    Example of PEM format:

    .. code-block::

        -----BEGIN CERTIFICATE-----
        Base64–encoded certificate
        -----END CERTIFICATE-----

Netconf-connector + Netopeer
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

`Netopeer <https://github.com/cesnet/netopeer>`__ (an open-source
NETCONF server) can be used for testing/exploring NETCONF southbound in
OpenDaylight.

Netopeer installation
^^^^^^^^^^^^^^^^^^^^^

A `Docker <https://www.docker.com/>`__ container with netopeer will be
used in this guide. To install Docker and start the `netopeer
image <https://hub.docker.com/r/sysrepo/sysrepo-netopeer2>`__ perform
following steps:

1. Install docker https://docs.docker.com/get-started/

2. Start the netopeer image:

   ::

       docker run -it --name sysrepo -p 830:830 --rm sysrepo/sysrepo-netopeer2:latest

3. Verify netopeer is running by invoking (netopeer should send its
   HELLO message right away:

   ::

       ssh root@localhost -p 830 -s netconf
       (password root)

Mounting netopeer NETCONF server
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Preconditions:

-  OpenDaylight is started with features ``odl-restconf-all`` and
   ``odl-netconf-connector-all``.

-  Netopeer is up and running in docker

Now just follow the section: `Spawning new NETCONF connectors`_ for
password authentication.
In the payload change the:

-  name, e.g., to netopeer

-  username/password to your system credentials

-  ip to localhost

-  port to 830.

After netopeer is mounted successfully, its configuration can be read
using RESTCONF by invoking:

GET
http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=netopeer/yang-ext:mount?content:config

Mounting netopeer NETCONF server using key-based authentication SSH
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. Install docker https://docs.docker.com/get-started/

2. Create RSA key pair - it will be user for connection.

3. Start the netopeer image(this command will also copy you pub key
   into docker container):

   ::

       docker run -dt -p 830:830 -v {path-to-pub-key}:/home/{netopeer-username}/.ssh/authorized_keys sysrepo/sysrepo-netopeer2:latest netopeer2-server -d -v 2

4. Verify netopeer is running by invoking (netopeer should send its
   HELLO message right away:

   ::

       ssh root@localhost -p 830 -s netconf
       (password root)

Now just follow the section: `Spawning new NETCONF connectors`_ for
key-based authentication(SSH) to create device.
In the payload change the:

-  name, e.g., to netopeer

-  username/password to your system credentials

-  ip to localhost

-  port to 830.

After netopeer is mounted successfully, its configuration can be read
using RESTCONF by invoking:

GET
http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=netopeer/yang-ext:mount?content:config

Mounting netopeer NETCONF server using key-based authentication TLS
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. Install docker https://docs.docker.com/get-started/

2. Run netopeer2

   ::

       docker pull sysrepo/sysrepo-netopeer2
       docker run -it --name sysrepo -p 830:830 -p 6513:6513 --rm sysrepo/sysrepo-netopeer2:latest

3. Enable TLS communication on server netopeer2

   ::

       ssh root@localhost -p 830 -s netconf
       (type password root)

   After successful connecting to netopeer2 setup your
   TLS configuration xml
   (See: https://github.com/CESNET/netopeer2/tree/master/example_configuration).

4. Run ODL:

-  :~/netconf/karaf/target/assembly/bin$ ./karaf

-  feature:install odl-netconf-topology odl-restconf-nb

5. Set up ODL netconf keystore

   To setup keystore is needed to send three RPCs from
   `Netconf-keystore configuration`_ section
   to add a client private key, associate a private key with a client and CA
   certificates chain and add a list of trusted CA and server certificates.

Now just follow the section: `Spawning new NETCONF connectors`_ for
key-based authentication(TLS) to create device.
In the payload change the:

-  name, e.g., to netopeer

-  username/password to your system credentials

-  ip to localhost

-  port to 6513.

After netopeer is mounted successfully, its configuration can be read
using RESTCONF by invoking:

GET
http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=netopeer/yang-ext:mount?content:config

Northbound (NETCONF servers)
----------------------------

OpenDaylight provides 2 types of NETCONF servers:

-  **NETCONF server for config-subsystem (listening by default on port
   1830)**

   -  Serves as a default interface for config-subsystem and allows
      users to spawn/reconfigure/destroy modules (or applications) in
      OpenDaylight

-  **NETCONF server for MD-SAL (listening by default on port 2830)**

   -  Serves as an alternative interface for MD-SAL (besides RESTCONF)
      and allows users to read/write data from MD-SAL’s datastore and to
      invoke its rpcs (NETCONF notifications are not available in the
      Boron release of OpenDaylight)

.. note::

    The reason for having 2 NETCONF servers is that config-subsystem and
    MD-SAL are 2 different components of OpenDaylight and require
    different approaches for NETCONF message handling and data
    translation. These 2 components will probably merge in the future.

.. note::

    Since Nitrogen release, there has been performance regression in NETCONF
    servers accepting SSH connections. While opening a connection takes
    less than 10 seconds on Carbon, on Nitrogen time can increase up to
    60 seconds. Please see https://jira.opendaylight.org/browse/ODLPARENT-112

NETCONF server for config-subsystem
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This NETCONF server is the primary interface for config-subsystem. It
allows the users to interact with config-subsystem in a standardized
NETCONF manner.

In terms of RFCs, these are supported:

-  `RFC-6241 <https://www.rfc-editor.org/rfc/rfc6241>`__

-  `RFC-5277 <https://www.rfc-editor.org/rfc/rfc5277>`__

-  `RFC-6470 <https://www.rfc-editor.org/rfc/rfc6470>`__

   -  (partially, only the schema-change notification is available in
      Boron release)

-  `RFC-6022 <https://www.rfc-editor.org/rfc/rfc6022>`__

For regular users it is recommended to use RESTCONF + the
controller-config loopback mountpoint instead of using pure NETCONF. How
to do that is specific for each component/module/application in
OpenDaylight and can be found in their dedicated user guides.

NETCONF server for MD-SAL
~~~~~~~~~~~~~~~~~~~~~~~~~

This NETCONF server is just a generic interface to MD-SAL in
OpenDaylight. It uses the standard MD-SAL APIs and serves as an
alternative to RESTCONF. It is fully model-driven and supports any data
and rpcs that are supported by MD-SAL.

In terms of RFCs, these are supported:

-  `RFC-6241 <https://www.rfc-editor.org/rfc/rfc6241>`__

-  `RFC-6022 <https://www.rfc-editor.org/rfc/rfc6022>`__

-  `RFC-7895 <https://www.rfc-editor.org/rfc/rfc7895>`__

Notifications over NETCONF are not supported in the Boron release.

.. tip::

    Install NETCONF northbound for MD-SAL by installing feature:
    ``odl-netconf-mdsal`` in karaf. Default binding port is **2830**.

Configuration
^^^^^^^^^^^^^

The default configuration can be found in file: *08-netconf-mdsal.xml*.
The file contains the configuration for all necessary dependencies and a
single SSH endpoint starting on port 2830. There is also a (by default
disabled) TCP endpoint. It is possible to start multiple endpoints at
the same time either in the initial configuration file or while
OpenDaylight is running.

The credentials for SSH endpoint can also be configured here, the
defaults are admin/admin. Credentials in the SSH endpoint are not yet
managed by the centralized AAA component and have to be configured
separately.

Verifying MD-SAL’s NETCONF server
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

After the NETCONF server is available it can be examined by a command
line ssh tool:

::

    ssh admin@localhost -p 2830 -s netconf

The server will respond by sending its HELLO message and can be used as
a regular NETCONF server from then on.

Mounting the MD-SAL’s NETCONF server
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

To perform this operation, just spawn a new netconf-connector as
described in `Spawning new NETCONF connectors`_. Just change the ip to
"127.0.0.1" port to "2830" and its name to "controller-mdsal".

Now the MD-SAL’s datastore can be read over RESTCONF via NETCONF by
invoking:

GET
http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=controller-mdsal/yang-ext:mount?content:nonconfig

.. note::

    This might not seem very useful, since MD-SAL can be accessed
    directly from RESTCONF or from Application code, but the same method
    can be used to mount and control other OpenDaylight instances by the
    "master OpenDaylight".

NETCONF stress/performance measuring tool
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This is basically a NETCONF client that puts NETCONF servers under heavy
load of NETCONF RPCs and measures the time until a configurable amount
of them is processed.

RESTCONF stress-performance measuring tool
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Very similar to NETCONF stress tool with the difference of using
RESTCONF protocol instead of NETCONF.

YANGLIB remote repository
-------------------------

There are scenarios in NETCONF deployment, that require for a centralized
YANG models repository. YANGLIB plugin provides such remote repository.

To start this plugin, you have to install odl-yanglib feature. Then you
have to configure YANGLIB either through RESTCONF or NETCONF. We will
show how to configure YANGLIB through RESTCONF.

YANGLIB configuration
~~~~~~~~~~~~~~~~~~~~~
YANGLIB configuration works through OSGi Configuration Admin interface, in the
``org.opendaylight.netconf.yanglib`` configuration PID. There are three tuneables you can
set:

* ``cache-folder``, which defaults to ``cache/schema``
* ``binding-address``, which defaults to ``localhost``
* ``binding-port``, which defaults to ``8181``

In order to change these settings, you can either modify the corresponding configuration
file, ``etc/org.opendaylight.netconf.yanglib.cfg``, for example:

::

    cache-folder = cache/newSchema
    binding-address = localhost
    binding-port = 8181

Or use Karaf CLI:

::

    opendaylight-user@root>config:edit org.opendaylight.netconf.yanglib
    opendaylight-user@root>config:property-set cache-folder cache/newSchema
    opendaylight-user@root>config:property-set binding-address localhost
    opendaylight-user@root>config:property-set binding-port 8181
    opendaylight-user@root>config:update

This YANGLIB takes all YANG sources from the configured sources folder and
for each generates URL in form:

::

    http://localhost:8181/yanglib/schemas/{modelName}/{revision}

On this URL will be hosted YANG source for particular module.

YANGLIB instance also writes this URL along with source identifier to
ietf-netconf-yang-library/modules-state/module list.

Netconf-connector with YANG library as fallback
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

There is an optional configuration in netconf-connector called
yang-library. You can specify YANG library to be plugged as additional
source provider into the mount's schema repository. Since YANGLIB
plugin is advertising provided modules through yang-library model, we
can use it in mount point's configuration as YANG library.  To do this,
we need to modify the configuration of netconf-connector by adding this
XML

::

    <yang-library xmlns="urn:opendaylight:netconf-node-topology">
      <yang-library-url xmlns="urn:opendaylight:netconf-node-topology">http://localhost:8181/rests/data/ietf-yang-library:modules-state</yang-library-url>
      <username xmlns="urn:opendaylight:netconf-node-topology">admin</username>
      <password xmlns="urn:opendaylight:netconf-node-topology">admin</password>
    </yang-library>

This will register YANGLIB provided sources as a fallback schemas for
particular mount point.

Restconf northbound configuration
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Restconf-nb configuration works through OSGi Configuration Admin interface, in the
``org.opendaylight.restconf.nb.rfc8040`` configuration PID. There are seven tuneables you can set:

* ``pretty-print``, which defaults to ``false``
* ``data-missing-is-404``, which defaults to ``false``
* ``maximum-fragment-length``, which defaults to ``0``
* ``heartbeat-interval``, which defaults to ``10000``
* ``restconf``, which defaults to ``rests``
* ``ping-executor-name-prefix``, which defaults to ``ping-executor``
* ``max-thread-count``, which defaults to ``1``

Netty endpoint related settings are also configurable:

* ``host-name``, which defaults to ``localhost``
* ``bind-address``, which defaults to ``0.0.0.0``
* ``bind-port``, which defaults to ``8182``
* ``group-name``, which defaults to ``restconf-server``
* ``group-threads``, which defaults to ``0``
* ``default-encoding``, which defaults to ``json``

Netty Tls transport configuration. Both certificate and private key are required in order to enable.

* ``tls-certificate``
* ``tls-private-key``


*pretty-print* — Control the default value of the odl-pretty-print query parameter.

*data-missing-is-404* — Control the HTTP status code reporting of conditions corresponding to "data-missing".
When this is set to true, the server will violate RFC8040 and report "404" instead of "409".

*maximum-fragment-length* — Maximum SSE fragment length in number of Unicode code units (characters)
(exceeded message length leads to fragmentation of messages)

*heartbeat-interval* — Interval in milliseconds between sending of ping control frames.

*restconf* — The value of RFC8040 restconf URI template, pointing to the root resource. Must not end with '/'.

*ping-executor-name-prefix* — Name of thread group Ping Executor will be run with.

*max-thread-count* — Number of threads Ping Executor will be run with.

*host-name* — The hostname to be used for URLs constructed on server side.

*bind-address* — The address to bind to.

*bind-port* — The port to bind to.

*group-name* — Thread name prefix to be used by Netty's thread executor.

*group-threads* — Netty's thread limit. 0 means no limits.

*default-encoding* — Default encoding for outgoing messages. Expected values are 'xml' or 'json' (without quotes).

*tls-certificate* — Path to the X509 certificate file in PEM format.

*tls-private-key* — Path to the private key file in PEM format.

In order to change these settings, you can either modify the corresponding configuration
file, ``org.opendaylight.restconf.nb.rfc8040.cfg``, for example:

::

    pretty-print=false
    data-missing-is-404=false
    maximum-fragment-length=0
    heartbeat-interval=10000
    restconf=rests
    ping-executor-name-prefix=ping-executor
    max-thread-count=1
    host-name=localhost
    bind-address=0.0.0.0
    bind-port=8182
    group-name=restconf-server
    group-threads=0
    default-encoding=json
    tls-certificate=etc/tls/cert.pem
    tls-private-key=etc/tls/key.pem

Or use Karaf CLI:

::

    opendaylight-user@root>config:edit org.opendaylight.restconf.nb.rfc8040
    opendaylight-user@root>config:property-set maximum-fragment_length 0
    opendaylight-user@root>config:property-set heartbeat-interval 10000
    opendaylight-user@root>config:property-set ping-executor-name-prefix "ping-executor"
    opendaylight-user@root>config:property-set max-thread-count 1
    opendaylight-user@root>config:property-set restconf "rests"
    opendaylight-user@root>config:property-set host-name "localhost"
    opendaylight-user@root>config:property-set bind-address "0.0.0.0"
    opendaylight-user@root>config:property-set bind-port 8182
    opendaylight-user@root>config:property-set group-name "restconf-server"
    opendaylight-user@root>config:property-set group-threads 0
    opendaylight-user@root>config:property-set default-encoding "json"
    opendaylight-user@root>config:property-set tls-certificate "etc/tls/cert.pem"
    opendaylight-user@root>config:property-set tls-private-key "etc/tls/key.pem"
    opendaylight-user@root>config:update

NETCONF Call Home
-----------------

Call Home Installation
~~~~~~~~~~~~~~~~~~~~~~

ODL Call-Home server is installed in Karaf by installing karaf feature
``odl-netconf-callhome-ssh``. RESTCONF feature is recommended for
configuring Call Home & testing its functionality.

::

  feature:install odl-netconf-callhome-ssh


.. note::

    In order to test Call Home functionality we recommend Netopeer or
    Netopeer2. See `Netopeer Call Home <https://github.com/CESNET/netopeer/wiki/CallHome>`__
    or `Netopeer2 <https://github.com/CESNET/netopeer2>`__ to learn how to
    enable call-home on Netopeer.

Northbound Call-Home API
~~~~~~~~~~~~~~~~~~~~~~~~

The northbound Call Home API is used for administering the Call-Home Server. The
following describes this configuration.

Global Configuration
^^^^^^^^^^^^^^^^^^^^

.. important::
  The global configuration is not a part of the `RFC 8071
  <https://www.rfc-editor.org/rfc/rfc8071>`__ and, therefore, subject to change.

Configuring global credentials
''''''''''''''''''''''''''''''

The ODL Call-Home server allows user to configure global credentials, which will be
used for devices connecting over SSH transport protocol that do not have
device-specific credentials configured.

This is done by creating
``/odl-netconf-callhome-server:netconf-callhome-server/global/credentials``
with username and passwords specified.

*Configuring global username & passwords to try*

.. code-block::

    PUT HTTP/1.1
    /rests/data/odl-netconf-callhome-server:netconf-callhome-server/global/credentials
    Content-Type: application/json
    Accept: application/json

.. code-block:: json

    {
      "credentials":
      {
        "username": "example",
        "passwords": [ "first-password-to-try", "second-password-to-try" ]
      }
    }

Configuring to accept any ssh server key using global credentials
'''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''

By default Netconf Call-Home Server accepts only incoming connections
from allowed devices
``/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices``,
if user desires to allow all incoming connections, it is possible to set
``accept-all-ssh-keys`` to ``true`` in
``/odl-netconf-callhome-server:netconf-callhome-server/global``.

The name of these devices in ``netconf-topology`` will be in format
``ip-address:port``. For naming devices see Device-Specific
Configuration.

*Allowing unknown devices to connect*

This is a debug feature and should not be used in production. Besides being an obvious
security issue, this also causes the Call-Home Server to drastically increase its output
to the log.

.. code-block::

    PUT HTTP/1.1
    /rests/data/odl-netconf-callhome-server:netconf-callhome-server/global/accept-all-ssh-keys
    Content-Type: application/json
    Accept: application/json

.. code-block:: json

    {
        "accept-all-ssh-keys": "true"
    }

Device-Specific Configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Netconf Call Home server supports both of the secure transports used
by the Network Configuration Protocol (NETCONF) - Secure Shell (SSH),
and Transport Layer Security (TLS).

Configure device to connect over SSH protocol
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Netconf Call Home Server uses device provided SSH server key (host key)
to identify device. The pairing of name and server key is configured in
``/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices``.
This list is colloquially called a allowlist.

If the Call-Home Server finds the SSH host key in the allowlist, it continues
to negotiate a NETCONF connection over an SSH session. If the SSH host key is
not found, the connection between the Call Home server and the device is dropped
immediately. In either case, the device that connects to the Call home server
leaves a record of its presence in the operational store.

Configuring Device with Device-specific Credentials
'''''''''''''''''''''''''''''''''''''''''''''''''''

Adding specific device to the allowed list is done by creating
``/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices/device={device}``
with device-id and connection parameters inside the ssh-client-params container.

*Configuring Device with Credentials*

.. code-block::

    PUT HTTP/1.1
    /rests/data/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices/device=example
    Content-Type: application/json
    Accept: application/json

.. code-block:: json

    {
      "device": {
        "unique-id": "example",
        "ssh-client-params": {
          "credentials": {
            "username": "example",
            "passwords": [ "password" ]
          },
          "host-key": "AAAAB3NzaC1yc2EAAAADAQABAAABAQDHoH1jMjltOJnCt999uaSfc48ySutaD3ISJ9fSECe1Spdq9o9mxj0kBTTTq+2V8hPspuW75DNgN+V/rgJeoUewWwCAasRx9X4eTcRrJrwOQKzb5Fk+UKgQmenZ5uhLAefi2qXX/agFCtZi99vw+jHXZStfHm9TZCAf2zi+HIBzoVksSNJD0VvPo66EAvLn5qKWQD4AdpQQbKqXRf5/W8diPySbYdvOP2/7HFhDukW8yV/7ZtcywFUIu3gdXsrzwMnTqnATSLPPuckoi0V2jd8dQvEcu1DY+rRqmqu0tEkFBurlRZDf1yhNzq5xWY3OXcjgDGN+RxwuWQK3cRimcosH"
        }
      }
    }

Configuring Device with Global Credentials
'''''''''''''''''''''''''''''''''''''''''''''''''''

It is possible to omit ``username`` and ``password`` for ssh-client-params,
in such case values from global credentials will be used.

*Example of configuring device*

.. code-block::

    PUT HTTP/1.1
    /rests/data/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices/device=example
    Content-Type: application/json
    Accept: application/json

.. code-block:: json

    {
      "device": {
        "unique-id": "example",
        "ssh-client-params": {
          "host-key": "AAAAB3NzaC1yc2EAAAADAQABAAABAQDHoH1jMjltOJnCt999uaSfc48ySutaD3ISJ9fSECe1Spdq9o9mxj0kBTTTq+2V8hPspuW75DNgN+V/rgJeoUewWwCAasRx9X4eTcRrJrwOQKzb5Fk+UKgQmenZ5uhLAefi2qXX/agFCtZi99vw+jHXZStfHm9TZCAf2zi+HIBzoVksSNJD0VvPo66EAvLn5qKWQD4AdpQQbKqXRf5/W8diPySbYdvOP2/7HFhDukW8yV/7ZtcywFUIu3gdXsrzwMnTqnATSLPPuckoi0V2jd8dQvEcu1DY+rRqmqu0tEkFBurlRZDf1yhNzq5xWY3OXcjgDGN+RxwuWQK3cRimcosH"
        }
      }
    }

Deprecated configuration models for devices accessed with SSH protocol
''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''

With `RFC 8071 <https://www.rfc-editor.org/rfc/rfc8071>`__ alignment and adding
support for TLS transport following configuration models have been marked
deprecated.

Configuring Device with Global Credentials
'''''''''''''''''''''''''''''''''''''''''''''''''''

*Example of configuring device*

.. code-block::

    PUT HTTP/1.1
    /rests/data/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices/device=example
    Content-Type: application/json
    Accept: application/json

.. code-block:: json

    {
      "device": {
        "unique-id": "example",
        "ssh-host-key": "AAAAB3NzaC1yc2EAAAADAQABAAABAQDHoH1jMjltOJnCt999uaSfc48ySutaD3ISJ9fSECe1Spdq9o9mxj0kBTTTq+2V8hPspuW75DNgN+V/rgJeoUewWwCAasRx9X4eTcRrJrwOQKzb5Fk+UKgQmenZ5uhLAefi2qXX/agFCtZi99vw+jHXZStfHm9TZCAf2zi+HIBzoVksSNJD0VvPo66EAvLn5qKWQD4AdpQQbKqXRf5/W8diPySbYdvOP2/7HFhDukW8yV/7ZtcywFUIu3gdXsrzwMnTqnATSLPPuckoi0V2jd8dQvEcu1DY+rRqmqu0tEkFBurlRZDf1yhNzq5xWY3OXcjgDGN+RxwuWQK3cRimcosH"
      }
    }

Configuring Device with Device-specific Credentials
'''''''''''''''''''''''''''''''''''''''''''''''''''

Call Home Server also allows the configuration of credentials per device basis.
This is done by introducing ``credentials`` container into the
device-specific configuration. Format is same as in global credentials.

*Configuring Device with Credentials*

.. code-block::

    PUT HTTP/1.1
    /rests/data/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices/device=example
    Content-Type: application/json
    Accept: application/json

.. code-block:: json

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

Configure device to connect over TLS protocol
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Netconf Call Home Server allows devices to use TLS transport protocol to
establish a connection towards the NETCONF device. This communication
requires proper setup to make two-way TLS authentication possible for client
and server.

The initial step is to configure certificates and keys for two-way TLS by
storing them within the netconf-keystore. How to configure keystore is described
in the `Netconf-keystore configuration`_ section.

In a second step, it is required to create an allowed device associated with
a server certificate and client key. The server certificate will be used to
identify and pin the NETCONF device during SSL handshake and should be unique
among the allowed devices.

*Add device configuration for TLS protocol to allowed devices list*

.. code-block::

    PUT HTTP/1.1
    /rests/data/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices/device=example-device
    Content-Type: application/json
    Accept: application/json

.. code-block:: json

  {
    "device": {
      "unique-id": "example-device",
      "tls-client-params": {
        "key-id": "example-client-key-id",
        "certificate-id": "example-server-certificate"
      }
    }
  }

Operational Status
^^^^^^^^^^^^^^^^^^

Once an entry is made on the config side of "allowed-devices", the Call-Home Server will
populate a corresponding operational device that is the same as the config device but
has an additional status. By default, this status is *DISCONNECTED*. Once a device calls
home, this status will change to one of:

*CONNECTED* — The device is currently connected and the NETCONF mount is available for network
management.

*FAILED_AUTH_FAILURE* — The last attempted connection was unsuccessful because the Call-Home
Server was unable to provide the acceptable credentials of the device. The device is also
disconnected and not available for network management.

*FAILED_NOT_ALLOWED* — The last attempted connection was unsuccessful because the device was
not recognized as an acceptable device. The device is also disconnected and not available for
network management.

*FAILED* — The last attempted connection was unsuccessful for a reason other than not
allowed to connect or incorrect client credentials. The device is also disconnected and not
available for network management.

*DISCONNECTED* — The device is currently disconnected.

Rogue Devices
'''''''''''''

Devices that are not on the allowlist might try to connect to the Call-Home Server. In
these cases, the server will keep a record by instantiating an operational device. There
will be no corresponding config device for these rogues. They can be identified readily
because their device id, rather than being user-supplied, will be of the form
"address:port". Note that if a device calls back multiple times, there will only be
a single operatinal entry (even if the port changes); these devices are recognized by
their unique host key.

Southbound Call-Home API
~~~~~~~~~~~~~~~~~~~~~~~~

The Call-Home Server listens for incoming TCP connections and assumes that the other side of
the connection is a device calling home via a NETCONF connection with SSH for
management. The server uses port 4334 by default and this can be configured via a
blueprint configuration file.

The device **must** initiate the connection and the server will not try to re-establish the
connection in case of a drop. By requirement, the server cannot assume it has connectivity
to the device due to NAT or firewalls among others.

Reading data with selected fields
---------------------------------

Overview
~~~~~~~~

If user would like to read only selected fields from a NETCONF device, it is possible to use
the fields query parameter that is described by RFC-8040. RESTCONF parses content of query
parameter into format that is accepted by NETCONF subtree filtering - filtering of data is done
on NETCONF server, not on NETCONF client side. This approach optimizes network traffic load,
because data in which user doesn't have interest, is not transferred over network.

Next advantages:

* using single RESTCONF request and single NETCONF RPC for reading multiple subtrees
* possibility to read only selected fields under list node across multiple hierarchies
  (it cannot be done without proper selection API)

.. note::

  More information about fields query parameter: `RFC 8071 <https://www.rfc-editor.org/rfc/rfc8040#section-4.8.3>`__

Preparation of data
~~~~~~~~~~~~~~~~~~~

For demonstration, we will define next YANG model:

::

    module test-module {
        yang-version 1.1;
        namespace "urn:opendaylight:test-module";
        prefix "tm";
        revision "2023-02-16";

        container root {
            container simple-root {
                leaf leaf-a {
                    type string;
                }
                leaf leaf-b {
                    type string;
                }
                leaf-list ll {
                    type string;
                }
                container nested {
                    leaf sample-x {
                        type boolean;
                    }
                    leaf sample-y {
                        type boolean;
                    }
                }
            }

            container list-root {
                leaf branch-ab {
                    type int32;
                }
                list top-list {
                    key "key-1 key-2";
                    ordered-by user;
                    leaf key-1 {
                        type string;
                    }
                    leaf key-2 {
                        type string;
                    }
                    container next-data {
                        leaf switch-1 {
                            type empty;
                        }
                        leaf switch-2 {
                            type empty;
                        }
                    }
                    list nested-list {
                        key "identifier";
                        leaf identifier {
                            type string;
                        }
                        leaf foo {
                            type int32;
                        }
                    }
                }
            }
        }
    }

Follow the :doc:`testtool` instructions to save this schema and run it with testtool.

Mounting NETCONF device that runs on NETCONF testtool:

.. code-block:: bash

  curl --location --request PUT 'http://127.0.0.1:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=testtool' \
  --header 'Authorization: Basic YWRtaW46YWRtaW4=' \
  --header 'Content-Type: application/json' \
  --data-raw '{
      "node": [
          {
              "node-id": "testtool",
              "netconf-node":{
                  "netconf-node-topology:host": "127.0.0.1",
                  "netconf-node-topology:port": 17830,
                  "netconf-node-topology:keepalive-delay": 100,
                  "netconf-node-topology:tcp-only": false,
                  "netconf-node-topology:login-password-unencrypted": {
                      "netconf-node-topology:username": "admin",
                      "netconf-node-topology:password": "admin"
                  }
              }
          }
      ]
  }'

Setting initial configuration on NETCONF device:

.. code-block:: bash

  curl --location --request PUT 'http://127.0.0.1:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=testtool/yang-ext:mount/test-module:root' \
  --header 'Authorization: Basic YWRtaW46YWRtaW4=' \
  --header 'Content-Type: application/json' \
  --data-raw '{
      "root": {
          "simple-root": {
              "leaf-a": "asddhg",
              "leaf-b": "ffffff",
              "ll": [
                  "str1",
                  "str2",
                  "str3"
              ],
              "nested": {
                  "sample-x": true,
                  "sample-y": false
              }
          },
          "list-root": {
              "branch-ab": 5,
              "top-list": [
                  {
                      "key-1": "ka",
                      "key-2": "kb",
                      "next-data": {
                          "switch-1": [
                              null
                          ],
                          "switch-2": [
                              null
                          ]
                      },
                      "nested-list": [
                          {
                              "identifier": "f1",
                              "foo": 1
                          },
                          {
                              "identifier": "f2",
                              "foo": 10
                          },
                          {
                              "identifier": "f3",
                              "foo": 20
                          }
                      ]
                  },
                  {
                      "key-1": "kb",
                      "key-2": "ka",
                      "next-data": {
                          "switch-1": [
                              null
                          ]
                      },
                      "nested-list": [
                          {
                              "identifier": "e1",
                              "foo": 1
                          },
                          {
                              "identifier": "e2",
                              "foo": 2
                          },
                          {
                              "identifier": "e3",
                              "foo": 3
                          }
                      ]
                  },
                  {
                      "key-1": "kc",
                      "key-2": "ke",
                      "next-data": {
                          "switch-2": [
                              null
                          ]
                      },
                      "nested-list": [
                          {
                              "identifier": "q1",
                              "foo": 13
                          },
                          {
                              "identifier": "q2",
                              "foo": 14
                          },
                          {
                              "identifier": "q3",
                              "foo": 15
                          }
                      ]
                  }
              ]
          }
      }
  }'

Examples
--------

1. Reading whole leaf-list 'll' and leaf 'nested/sample-x' under 'simple-root' container.

RESTCONF request:

.. code-block:: bash

    curl --location --request GET 'http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=testtool/yang-ext:mount/test-module:root/simple-root?content=config&fields=ll;nested/sample-x' \
    --header 'Authorization: Basic YWRtaW46YWRtaW4=' \
    --header 'Cookie: JSESSIONID=node01h4w82eorc1k61866b71qjgj503.node0'

Generated NETCONF RPC request:

.. code-block:: xml

    <rpc message-id="m-18" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
        <get-config>
            <source>
                <running/>
            </source>
            <filter xmlns:ns0="urn:ietf:params:xml:ns:netconf:base:1.0" ns0:type="subtree">
                <root xmlns="urn:ietf:params:xml:ns:yang:test-model">
                    <simple-root>
                        <ll/>
                        <nested>
                            <sample-x/>
                        </nested>
                    </simple-root>
                </root>
            </filter>
        </get-config>
    </rpc>

.. note::

    Using fields query parameter it is also possible to read whole leaf-list or list without
    necessity to specify value / key predicate (without reading parent entity). Such scenario
    is not permitted in RFC-8040 paths alone - fields query parameter can be used as
    workaround for this case.

RESTCONF response:

.. code-block:: json

    {
        "test-module:simple-root": {
            "ll": [
                "str3",
                "str1",
                "str2"
            ],
            "nested": {
                "sample-x": true
            }
        }
    }

2. Reading all identifiers of 'nested-list' under all elements of 'top-list'.

RESTCONF request:

.. code-block:: bash

    curl --location --request GET 'http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=testtool/yang-ext:mount/test-module:root/list-root?content=config&fields=top-list(nested-list/identifier)' \
    --header 'Authorization: Basic YWRtaW46YWRtaW4=' \
    --header 'Cookie: JSESSIONID=node01h4w82eorc1k61866b71qjgj503.node0'

Generated NETCONF RPC request:

.. code-block:: xml

    <rpc message-id="m-27" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
        <get-config>
            <source>
                <running/>
            </source>
            <filter xmlns:ns0="urn:ietf:params:xml:ns:netconf:base:1.0" ns0:type="subtree">
                <root xmlns="urn:ietf:params:xml:ns:yang:test-model">
                    <list-root>
                        <top-list>
                            <nested-list>
                                <identifier/>
                            </nested-list>
                            <key-1/>
                            <key-2/>
                        </top-list>
                    </list-root>
                </root>
            </filter>
        </get-config>
    </rpc>

.. note::

    NETCONF client automatically fetches values of list keys since they are required for correct
    deserialization of NETCONF response and at the end serialization of response to RESTCONF
    response (JSON/XML).

RESTCONF response:

.. code-block:: json

    {
        "test-module:list-root": {
            "top-list": [
                {
                    "key-1": "ka",
                    "key-2": "kb",
                    "nested-list": [
                        {
                            "identifier": "f3"
                        },
                        {
                            "identifier": "f2"
                        },
                        {
                            "identifier": "f1"
                        }
                    ]
                },
                {
                    "key-1": "kb",
                    "key-2": "ka",
                    "nested-list": [
                        {
                            "identifier": "e3"
                        },
                        {
                            "identifier": "e2"
                        },
                        {
                            "identifier": "e1"
                        }
                    ]
                },
                {
                    "key-1": "kc",
                    "key-2": "ke",
                    "nested-list": [
                        {
                            "identifier": "q3"
                        },
                        {
                            "identifier": "q2"
                        },
                        {
                            "identifier": "q1"
                        }
                    ]
                }
            ]
        }
    }

3. Reading value of leaf 'branch-ab' and all values of leaves 'switch-1' that are placed
   under 'top-list' list elements.

RESTCONF request:

.. code-block:: bash

    curl --location --request GET 'http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf/node=testtool/yang-ext:mount/test-module:root/list-root?content=config&fields=branch-ab;top-list/next-data/switch-1' \
    --header 'Authorization: Basic YWRtaW46YWRtaW4=' \
    --header 'Cookie: JSESSIONID=node01jx6o5thwae9t1ft7c2zau5zbz4.node0'

Generated NETCONF RPC request:

.. code-block:: xml

    <rpc message-id="m-42" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
        <get-config>
            <source>
                <running/>
            </source>
            <filter xmlns:ns0="urn:ietf:params:xml:ns:netconf:base:1.0" ns0:type="subtree">
                <root xmlns="urn:ietf:params:xml:ns:yang:test-model">
                    <list-root>
                        <branch-ab/>
                        <top-list>
                            <next-data>
                                <switch-1/>
                            </next-data>
                            <key-1/>
                            <key-2/>
                        </top-list>
                    </list-root>
                </root>
            </filter>
        </get-config>
    </rpc>

RESTCONF response:

.. code-block:: json

    {
        "test-module:list-root": {
            "branch-ab": 5,
            "top-list": [
                {
                    "key-1": "ka",
                    "key-2": "kb",
                    "next-data": {
                        "switch-1": [
                            null
                        ]
                    }
                },
                {
                    "key-1": "kb",
                    "key-2": "ka",
                    "next-data": {
                        "switch-1": [
                            null
                        ]
                    }
                },
                {
                    "key-1": "kc",
                    "key-2": "ke"
                }
            ]
        }
    }

Reading module source
---------------------

Overview
~~~~~~~~

If user would like to read module source from a Controller or NETCONF device, it is possible to use
the subpath "modules". Revision of the module is optional, so it is passed as a query parameter. There is
also a possibility to read modules in yang format or in yin format.

*Read module source from controller*

.. code-block::

    GET
    /rests/modules/{module-name}?revision={revision}
    Accept: application/yang or application/yin+xml

*Read mounted module source from device*

.. code-block::

    GET
    /rests/modules/network-topology:network-topology/topology=topology-netconf/node={node-id}/yang-ext:mount/{module-name}?revision={revision}
    Accept: application/yang or application/yin+xml

RESTCONF OpenAPI
----------------

Overview
~~~~~~~~

The OpenAPI provides full API for configurational data which can be edited (by POST, PUT, PATCH and DELETE).
For operational data we only provide GET API. For the majority of requests you can see only config data in examples.
That’s because we can show only one example per request. The exception when you can see operational data in an
example is when data are representing an operational (config false) container with no config data in it.


Using the OpenAPI Explorer through HTTP
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

1. Install OpenApi into Karaf by installing karaf feature:

::

    $ feature:install odl-restconf-openapi

2.  Navigate to OpenAPI in your web browser which is available at URLs:

-  http://localhost:8181/openapi/explorer/index.html for general overview

-  http://localhost:8181/openapi/api/v3/single for JSON data

.. note::

    In the URL links for OpenAPI, change *localhost* to the IP/Host name of your actual server.

3.  Enter the username and password.
    By default the credentials are  *admin/admin*.

4.  Select any model to try out.

5.  Select any available request to try out.

6.  Click on the **Try it out** button.

7.  Provide any required parameters or edit request body.

8.  Click the **Execute** button.

9.  You can see responses to the given request.


OpenAPI Explorer can also be used for connected device. How to connect a device can be found :ref:`here <netconf-connector>`.

OpenAPI URLs in that case would look like this:

-  `http://localhost:8181/openapi/explorer/index.html?urls.primaryName=17830-sim-device resources - RestConf RFC 8040 <http://localhost:8181/openapi/explorer/index.html?urls.primaryName=17830-sim-device%20resources%20-%20RestConf%20RFC%208040>`_ for device overview

-  http://localhost:8181/openapi/api/v3/mounts/1 for JSON data

-  `http://localhost:8181/openapi/api/v3/mounts/1/toaster?revision=2009-11-20 <http://localhost:8181/openapi/api/v3/mounts/1/toaster?revision=2009-11-20>`__ JSON data for given model

-  `http://localhost:8181/openapi/api/v3/mounts/1/definition-test <http://localhost:8181/openapi/api/v3/mounts/1/definition-test>`__ JSON data for given model without revision

.. note::

    The URL links for OpenAPI are made for device with name *17830-sim-device* and model toaster
    with *2009-11-20* revision and need to be changed accordingly to connected device.
