ANGLIB Remote Repository
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
