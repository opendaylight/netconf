=========================
YANGLIB remote repository
=========================

There are scenarios in NETCONF deployment, that require for a centralized
YANG models repository. YANGLIB plugin provides such remote repository.

To start this plugin, you have to install odl-yanglib feature. Then you
have to configure YANGLIB either through RESTCONF or NETCONF. We will
show how to configure YANGLIB through RESTCONF.

YANGLIB configuration through RESTCONF
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

You have to specify what local YANG modules directory you want to provide.
Then you have to specify address and port whre you want to provide YANG
sources. For example, we want to serve yang sources from folder /sources
on localhost:5000 adress. The configuration for this scenario will be
as follows:

::

    PUT  http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config/yang-ext:mount/config:modules/module/yanglib:yanglib/example

Headers:

-  Accept: application/xml

-  Content-Type: application/xml

Payload:

::

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

This should result in a 2xx response and new YANGLIB instance should be
created. This YANGLIB takes all YANG sources from /sources folder and
for each generates URL in form:

::

    http://localhost:5000/schemas/{modelName}/{revision}

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
      <yang-library-url xmlns="urn:opendaylight:netconf-node-topology">http://localhost:8181/restconf/operational/ietf-yang-library:modules-state</yang-library-url>
      <username xmlns="urn:opendaylight:netconf-node-topology">admin</username>
      <password xmlns="urn:opendaylight:netconf-node-topology">admin</password>
    </yang-library>

This will register YANGLIB provided sources as a fallback schemas for
particular mount point.
