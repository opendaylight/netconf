.. _utilize-netconf:

===========================
Utilize a NETCONF-Connector
===========================

Once a NETCONF-connector is up-and-running, users can utilize this mount point instance via
RESTCONF or from their application code. 

.. note:: For more information on utilizing the NETCONF-Connector, refer to the NETCONF developers
          guide or the `ncmount <https://github.com/opendaylight/coretutorials/tree/master/ncmount>`_
          core tutorials project.

Reading Data
------------

Issue the following command to read NETCONF invoke. The ``body`` is not needed:

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8080/restconf/operational/network-topology:network-topology/topology/topology-netconf/node/new-netconf-device/yang-ext:mount/``

**Method:** ``GET``

This returns the entire content of the operation datastore from a device. To view just the
configuration datastore, change ``operational`` in this URL to ``config``.

Writing Configuration Data
--------------------------

In general, you cannot simply write data to a device. Instead, the data must conform to
YANG models implemented by a specific device. In the following example, a new ``interface-configuration``
is added to a mounted device. 

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/new-netconf-device/yang-ext:mount/Cisco-IOS-XR-ifmgr-cfg:interface-configurations``

**Payload**

.. code-block: none

   <interface-configuration xmlns="http://cisco.com/ns/yang/Cisco-IOS-XR-ifmgr-cfg">
      <active>act</active>
      <interface-name>mpls</interface-name>
      <description>Interface description</description>
     <bandwidth>32</bandwidth>
    <link-status></link-status>
   </interface-configuration>

This should return a ``200`` response code with no ``body``.

.. note:: This call is transformed into a couple of NETCONF RPCs. The resulting NETCONF RPCs can be
          found in the OpenDaylight logs after invoking 
          ``log:set TRACE org.opendaylight.controller.sal.connect.netconf`` in the Karaf shell.

This request is similar to the one when :ref:`create-a-netconf`, since it uses the loopback
`netconf-connector` to write configuration data into config-subsystem datastore.
