NETCONF-connector and Netopeer
==============================

Netopeer is an open-source NETCONF server. It can be used to test/explore NETCONF
southbound. For information on installing Netopeer, refer to `Set up Netopeer Server
<http://www.seguesoft.com/index.php/how-to-set-up-netopeer-server-to-use-with-netconfc>`_.
Before using Netopeer, ensure that both ``odl-restconf-all`` and ``odl-netconf-connector-all``
are installed, and that Netopeer is up-and-running in Docker. Send the following request to
RESTCONF to create a new NETCONF connector using MD-SAL. Ensure that the device's name in
``<node-id>`` matches the last element of the URL.

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8131/restconf/operational/network-topology:network-topology/topology/netopeer/``

**Method:** ``PUT``

After Netopeer is mounted successfully, read its configuration by invoking the following:

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/netopeer/yang-ext:mount/``

**Method:** ``GET``
