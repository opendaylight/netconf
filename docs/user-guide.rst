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

Payload:

.. tabs::

   .. tab:: XML

      **Content-type:** ``application/xml``

      **Accept:** ``application/xml``

      **Authentication:** ``admin:admin``

      .. code-block:: xml

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

   .. tab:: JSON

      **Content-type:** ``application/json``

      **Accept:** ``application/json``

      **Authentication:** ``admin:admin``

      .. code-block:: json

         {
             "node": [
                 {
                     "node-id": "new-netconf-device",
                     "netconf-node-topology:port": 17830,
                     "netconf-node-topology:reconnect-on-changed-schema": false,
                     "netconf-node-topology:connection-timeout-millis": 20000,
                     "netconf-node-topology:tcp-only": false,
                     "netconf-node-topology:max-connection-attempts": 0,
                     "netconf-node-topology:username": "admin",
                     "netconf-node-topology:password": "admin",
                     "netconf-node-topology:sleep-factor": 1.5,
                     "netconf-node-topology:host": "127.0.0.1",
                     "netconf-node-topology:between-attempts-timeout-millis": 2000,
                     "netconf-node-topology:keepalive-delay": 120
                 }
             ]
         }

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
           <host xmlns="urn:opendaylight:netconf-node-topology">127.0.0.1</host>
           <port xmlns="urn:opendaylight:netconf-node-topology">8305</port>
           <username xmlns="urn:opendaylight:netconf-node-topology">root</username>
           <password xmlns="urn:opendaylight:netconf-node-topology">root</password>
           <tcp-only xmlns="urn:opendaylight:netconf-node-topology">false</tcp-only>
           <keepalive-delay xmlns="urn:opendaylight:netconf-node-topology">30</keepalive-delay>
           <yang-module-capabilities xmlns="urn:opendaylight:netconf-node-topology">
             <override>true</override>
             <capability xmlns="urn:opendaylight:netconf-node-topology">
               urn:ietf:params:xml:ns:yang:ietf-inet-types?module=ietf-inet-types&amp;revision=2013-07-15
             </capability>
           </yang-module-capabilities>
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
                     "netconf-node-topology:host": "127.0.0.1",
                     "netconf-node-topology:password": "root",
                     "netconf-node-topology:username": "root",
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
mountpoints. Using RESTCONF with such devices is not suported. Also
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

Devices emit netconf alarms and notifictions on certain situtations, which can demand
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

The response suggests the http url for reading the notifications.

.. code-block:: json

    {
       "odl-device-notification:output": {
            "stream-path": "http://localhost:8181/rests/notif/test_device?notificationType=test_device"
        }
    }

- Step 5: User can access the url in the response and the notifications will be as follows.

.. code-block::

    GET
    http://localhost:8181/rests/notif/test_device?notificationType=test_device
    Content-Type: application/xml
    Accept: application/xml


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
OpenDaylight is running. OpenDaylight offers two methods for receiving notifications:
Server-Sent Events (SSE) and WebSocket. SSE is the default notification mechanism used in OpenDaylight.

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
            "stream-name": "data-change-event-subscription/toaster:toaster/toaster:toasterStatus/datastore=CONFIGURATION/scope=SUBTREE"
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
`http://{odlAddress}:{odlPort}/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream/{streamName}`,
where *{streamName}* is the *stream-name* parameter contained in
response from *create-data-change-event-subscription* RPC from the
previous step.

::

   OPERATION: GET
   URI: http://{odlAddress}:{odlPort}/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream/data-change-event-subscription/toaster:toaster/datastore=CONFIGURATION/scope=SUBTREE

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

The response should look something like this:

.. code-block:: json

    {
        "subscribe-to-notification:location": "http://localhost:8181/rests/notif/data-change-event-subscription/network-topology:network-topology/datastore=CONFIGURATION/scope=SUBTREE"
    }

.. note::

    During this phase there is an internal check for to see if a
    listener for the *stream-name* from the URI exists. If not, new a
    new listener is registered with the DOM data broker.

Receive notifications
^^^^^^^^^^^^^^^^^^^^^

Once you got SSE location you can now connect to it and
start receiving data change events. The request should look something like this:

::

    curl -v -X GET  http://localhost:8181/rests/notif/data-change-event-subscription/toaster:toaster/toasterStatus/datastore=OPERATIONAL/scope=ONE  -H "Content-Type: text/event-stream" -H "Authorization: Basic YWRtaW46YWRtaW4="


WebSocket notifications subscription process
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Enabling WebSocket notifications in OpenDaylight requires a manual setup before starting the application.
The following steps can be followed to enable WebSocket notifications in OpenDaylight:

1. Open the file `restconf8040.cfg`, at `etc/` folder inside your Karaf distribution.
2. Locate the `use-sse` configuration parameter and change its value from `true` to `false`.
3. Uncomment the `use-sse` parameter if it is commented out.
4. Save the changes made to the `restconf8040.cfg` file.
5. Restart OpenDaylight if it is already running.

Once these steps are completed, WebSocket notifications will be enabled in OpenDaylight,
and they can be used for receiving notifications instead of SSE.

WebSocket Notifications subscription process is the same as SSE until you receive a location of WebSocket.
You can follow steps given above and after subscribing to a notification stream over WebSocket,
you will receive a response indicating that the subscription was successful:

.. code-block:: json

    {
        "subscribe-to-notification:location": "ws://localhost:8181/rests/notif/data-change-event-subscription/network-topology:network-topology/datastore=CONFIGURATION/scope=SUBTREE"
    }

You can use this WebSocket to listen to data
change notifications. To listen to notifications you can use a
JavaScript client or if you are using chrome browser you can use the
`Simple WebSocket
Client <https://chrome.google.com/webstore/detail/simple-websocket-client/pfdhoblngboilpfeibdedpjgfnlcodoo>`__.

Also, for testing purposes, there is simple Java application named
WebSocketClient. The application is placed in the
*/restconf/websocket-client* project. It accepts a WebSocket URI
as and input parameter. After starting the utility (WebSocketClient
class directly in Eclipse/InteliJ Idea) received notifications should be
displayed in console.

Notifications are always in XML format and look like this:

.. code-block:: xml

    <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">
        <eventTime>2014-09-11T09:58:23+02:00</eventTime>
        <data-changed-notification xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote">
            <data-change-event>
                <path xmlns:meae="http://netconfcentral.org/ns/toaster">/meae:toaster</path>
                <operation>updated</operation>
                <data>
                   <!-- updated data -->
                </data>
            </data-change-event>
        </data-changed-notification>
    </notification>

Example use case
~~~~~~~~~~~~~~~~

The typical use case is listening to data change events to update web
page data in real-time. In this tutorial we will be using toaster as the
base.

When you call *make-toast* RPC, it sets *toasterStatus* to "down" to
reflect that the toaster is busy making toast. When it finishes,
*toasterStatus* is set to "up" again. We will listen to this toaster
status changes in data store and will reflect it on our web page in
real-time thanks to WebSocket data change notification.

Simple javascript client implementation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

We will create simple JavaScript web application that will listen
updates on *toasterStatus* leaf and update some element of our web page
according to new toaster status state.

Create stream
^^^^^^^^^^^^^

First you need to create stream that you are planing to subscribe to.
This can be achieved by invoking "create-data-change-event-subscription"
RPC on RESTCONF via AJAX request. You need to provide data store
**path** that you plan to listen on, **data store type** and **scope**.
If the request is successful you can extract the **stream-name** from
the response and use that to subscribe to the newly created stream. The
*{username}* and *{password}* fields represent your credentials that you
use to connect to OpenDaylight via RESTCONF:

.. note::

    The default user name and password are "admin".

.. code-block:: javascript

    function createStream() {
        $.ajax(
            {
                url: 'http://{odlAddress}:{odlPort}/rests/operations/sal-remote:create-data-change-event-subscription',
                type: 'POST',
                headers: {
                  'Authorization': 'Basic ' + btoa('{username}:{password}'),
                  'Content-Type': 'application/json'
                },
                data: JSON.stringify(
                    {
                        'input': {
                            'path': '/toaster:toaster/toaster:toasterStatus',
                            'sal-remote-augment:datastore': 'OPERATIONAL',
                            'sal-remote-augment:scope': 'ONE'
                        }
                    }
                )
            }).done(function (data) {
                // this function will be called when ajax call is executed successfully
                subscribeToStream(data.output['stream-name']);
            }).fail(function (data) {
                // this function will be called when ajax call fails
                console.log("Create stream call unsuccessful");
            })
    }

Subscribe to stream
^^^^^^^^^^^^^^^^^^^

The Next step is to subscribe to the stream. To subscribe to the stream
you need to call *GET* on
*http://{odlAddress}:{odlPort}/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream/{stream-name}*.
If the call is successful, you get WebSocket address for this stream in
**Location** parameter inside response header. You can get response
header by calling *getResponseHeader(\ *Location*)* on HttpRequest
object inside *done()* function call:

.. code-block:: javascript

    function subscribeToStream(streamName) {
        $.ajax(
            {
                url: 'http://{odlAddress}:{odlPort}/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream/' + streamName;
                type: 'GET',
                headers: {
                  'Authorization': 'Basic ' + btoa('{username}:{password}'),
                }
            }
        ).done(function (data, textStatus, httpReq) {
            // we need function that has http request object parameter in order to access response headers.
            listenToNotifications(httpReq.getResponseHeader('Location'));
        }).fail(function (data) {
            console.log("Subscribe to stream call unsuccessful");
        });
    }

Receive notifications
^^^^^^^^^^^^^^^^^^^^^

Once you got WebSocket server location you can now connect to it and
start receiving data change events. You need to define functions that
will handle events on WebSocket. In order to process incoming events
from OpenDaylight you need to provide a function that will handle
*onmessage* events. The function must have one parameter that represents
the received event object. The event data will be stored in
*event.data*. The data will be in an XML format that you can then easily
parse using jQuery.

.. code-block:: javascript

    function listenToNotifications(socketLocation) {
        try {
            var notificatinSocket = new WebSocket(socketLocation);

            notificatinSocket.onmessage = function (event) {
                // we process our received event here
                console.log('Received toaster data change event.');
                $($.parseXML(event.data)).find('data-change-event').each(
                    function (index) {
                        var operation = $(this).find('operation').text();
                        if (operation == 'updated') {
                            // toaster status was updated so we call function that gets the value of toasterStatus leaf
                            updateToasterStatus();
                            return false;
                        }
                    }
                );
            }
            notificatinSocket.onerror = function (error) {
                console.log("Socket error: " + error);
            }
            notificatinSocket.onopen = function (event) {
                console.log("Socket connection opened.");
            }
            notificatinSocket.onclose = function (event) {
                console.log("Socket connection closed.");
            }
            // if there is a problem on socket creation we get exception (i.e. when socket address is incorrect)
        } catch(e) {
            alert("Error when creating WebSocket" + e );
        }
    }

The *updateToasterStatus()* function represents function that calls
*GET* on the path that was modified and sets toaster status in some web
page element according to received data. After the WebSocket connection
has been established you can test events by calling make-toast RPC via
RESTCONF.

.. note::

    for more information about WebSockets in JavaScript visit `Writing
    WebSocket client
    applications <https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API/Writing_WebSocket_client_applications>`__

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

Now just follow the section: `Spawning new NETCONF connectors`_.
In the payload change the:

-  name, e.g., to netopeer

-  username/password to your system credentials

-  ip to localhost

-  port to 1831.

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
    different approach for NETCONF message handling and data
    translation. These 2 components will probably merge in the future.

.. note::

    Since Nitrogen release, there is performance regression in NETCONF
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
to do that is spesific for each component/module/application in
OpenDaylight and can be found in their dedicated user guides.

NETCONF server for MD-SAL
~~~~~~~~~~~~~~~~~~~~~~~~~

This NETCONF server is just a generic interface to MD-SAL in
OpenDaylight. It uses the stadard MD-SAL APIs and serves as an
alternative to RESTCONF. It is fully model driven and supports any data
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

YANGLIB instance also write this URL along with source identifier to
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

ODL Call-Home server allows user to configure global credentials, which will be
used for connected over SSH transport protocol devices which does not have
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
if user desire to allow all incoming connections, it is possible to set
``accept-all-ssh-keys`` to ``true`` in
``/odl-netconf-callhome-server:netconf-callhome-server/global``.

The name of this devices in ``netconf-topology`` will be in format
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
This list is colloquially called a whitelist.

If the Call-Home Server finds the SSH host key in the whitelist, it continues
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
support for TLS transport following configuration models has been marked
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

Call Home Server also allows to configure credentials per device basis,
this is done by introducing ``credentials`` container into
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
storing them within the netconf-keystore.

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
          "private-key": "base64encoded-private-key",
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

Once an entry is made into the config side of "allowed-devices", the Call-Home Server will
populate an corresponding operational device that is the same as the config device but
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

Devices which are not on the whitelist might try to connect to the Call-Home Server. In
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
management. The server uses port 6666 by default and this can be configured via a
blueprint configuration file.

The device **must** initiate the connection and the server will not try to re-establish the
connection in case of a drop. By requirement, the server cannot assume it has connectivity
to the device due to NAT or firewalls among others.

Reading data with selected fields
---------------------------------

Overview
~~~~~~~~

If user would like to read only selected fields from NETCONF device, it is possible to use
fields query parameter that is described by RFC-8040. RESTCONF parses content of query
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
              "netconf-node-topology:host": "127.0.0.1",
              "netconf-node-topology:port": 17830,
              "netconf-node-topology:keepalive-delay": 100,
              "netconf-node-topology:tcp-only": false,
              "netconf-node-topology:username": "admin",
              "netconf-node-topology:password": "admin"
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
