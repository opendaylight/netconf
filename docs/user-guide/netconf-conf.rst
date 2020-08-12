===============================
Netconf-connector configuration
===============================

There are 2 ways for configuring netconf-connector: NETCONF or RESTCONF.
This guide focuses on using RESTCONF.

.. important::

    There are 2 different endpoints related to RESTCONF protocols:

    - | ``http://localhost:8181/restconf`` is related to `draft-bierman-netconf-restconf-02 <https://tools.ietf.org/html/draft-bierman-netconf-restconf-02>`__,
      | can be activated by installing ``odl-restconf-nb-bierman02``
       Karaf feature.
      | This user guide uses this approach.

    - | ``http://localhost:8181/rests`` is related to `RFC-8040 <https://tools.ietf.org/html/rfc8040>`__,
      | can be activated by installing ``odl-restconf-nb-rfc8040``
       Karaf feature.

    | In case of `RFC-8040 <https://tools.ietf.org/html/rfc8040>`__
     resources for configuration and operational datastores start
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

    | Also in case of `RFC-8040 <https://tools.ietf.org/html/rfc8040>`__,
     if a data node in the path expression is a YANG leaf-list or list
     node, the path segment has to be constructed by having leaf-list or
     list node name, followed by an "=" character, then followed by the
     leaf-list or list value. Any reserved characters must be
     percent-encoded.
    | e. g. GET
     http://localhost:8181/rests/data/network-topology:network-topology/topology=topology-netconf?content=config
     for retrieving data from configuration datastore for
     topology-netconf value of topology list is equivalent to the deprecated request
    | e.g. GET
     http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf,
     which is related to `draft-bierman-netconf-restconf-02
     <https://tools.ietf.org/html/draft-bierman-netconf-restconf-02>`__.

    Examples in the :ref:`spawning-new-connector` section include both bierman02 and rfc8040
    formats


Default configuration
^^^^^^^^^^^^^^^^^^^^^

The default configuration contains all the necessary dependencies (file:
01-netconf.xml) and a single instance of netconf-connector (file:
99-netconf-connector.xml) called **controller-config** which connects
itself to the NETCONF northbound in OpenDaylight in a loopback fashion.
The connector mounts the NETCONF server for config-subsystem in order to
enable RESTCONF protocol for config-subsystem. This RESTCONF still goes
via NETCONF, but using RESTCONF is much more user friendly than using
NETCONF.

Spawning additional netconf-connectors while the controller is running
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Preconditions:

1. OpenDaylight is running

2. In Karaf, you must have the netconf-connector installed (at the Karaf
   prompt, type: ``feature:install odl-netconf-connector-all``); the
   loopback NETCONF mountpoint will be automatically configured and
   activated

3. Wait until log displays following entry:
   RemoteDevice{controller-config}: NETCONF connector initialized
   successfully

To configure a new netconf-connector you need to send following request
to RESTCONF:

POST
http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config/yang-ext:mount/config:modules

Headers:

-  Accept application/xml

-  Content-Type application/xml

::

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

This spawns a new netconf-connector which tries to connect to (or mount)
a NETCONF device at 127.0.0.1 and port 830. You can check the
configuration of config-subsystem’s configuration datastore. The new
netconf-connector will now be present there. Just invoke:

GET
http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config/yang-ext:mount/config:modules

The response will contain the module for new-netconf-device.

Right after the new netconf-connector is created, it writes some useful
metadata into the datastore of MD-SAL under the network-topology
subtree. This metadata can be found at:

GET
http://localhost:8181/restconf/operational/network-topology:network-topology/

Information about connection status, device capabilities, etc. can be
found there.

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
configuration of netconf-connector by adding this XML (It needs to be
added next to the address, port, username etc. configuration elements):

::

    <yang-module-capabilities xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
      <capability xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
        urn:ietf:params:xml:ns:yang:ietf-inet-types?module=ietf-inet-types&amp;revision=2010-09-24
      </capability>
    </yang-module-capabilities>

**Remember to also put the YANG schemas into the cache folder.**

.. note::

    For putting multiple capabilities, you just need to replicate the
    capability xml element inside yang-module-capability element.
    Capability element is modeled as a leaf-list. With this
    configuration, we would make the remote device report usage of
    ietf-inet-types in the eyes of netconf-connector.

Reconfiguring Netconf-Connector While the Controller is Running
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

It is possible to change the configuration of a running module while the
whole controller is running. This example will continue where the last
left off and will change the configuration for the brand new
netconf-connector after it was spawned. Using one RESTCONF request, we
will change both username and password for the netconf-connector.

To update an existing netconf-connector you need to send following
request to RESTCONF:

PUT
http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config/yang-ext:mount/config:modules/module/odl-sal-netconf-connector-cfg:sal-netconf-connector/new-netconf-device

::

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

Since a PUT is a replace operation, the whole configuration must be
specified along with the new values for username and password. This
should result in a 2xx response and the instance of netconf-connector
called new-netconf-device will be reconfigured to use username bob and
password passwd. New configuration can be verified by executing:

GET
http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config/yang-ext:mount/config:modules/module/odl-sal-netconf-connector-cfg:sal-netconf-connector/new-netconf-device

With new configuration, the old connection will be closed and a new one
established.

Destroying Netconf-Connector While the Controller is Running
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Using RESTCONF one can also destroy an instance of a module. In case of
netconf-connector, the module will be destroyed, NETCONF connection
dropped and all resources will be cleaned. To do this, simply issue a
request to following URL:

DELETE
http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config/yang-ext:mount/config:modules/module/odl-sal-netconf-connector-cfg:sal-netconf-connector/new-netconf-device

The last element of the URL is the name of the instance and its
predecessor is the type of that module (In our case the type is
**sal-netconf-connector** and name **new-netconf-device**). The type and
name are actually the keys of the module list.
