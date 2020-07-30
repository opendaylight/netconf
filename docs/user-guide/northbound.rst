.. _northbound:

Northbound Server
-----------------

A northbound server acta as a northbound interface for NETCONF. There are two types of NETCONF
servers: :ref:`config-subsystem` and :ref:`config-mdsal`.

.. _config-subsystem:

NETCONF Server for Config-subsystem
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

By default, the NETCONF server for config-subsystem listens on port 1830.
It serves as a default interface for config-subsystem to allow users to create,
reconfigure, and delete modules or applications.

.. _config-mdsal:

NETCONF Server for MD-SAL
^^^^^^^^^^^^^^^^^^^^^^^^^

The NETCONF server for MD-SAL listens on port 2830. This server acts as an alternative
interface for MD-SAL (besides RESTCONF) to allow users to read/write data from
MD-SAL’s datastore and to invoke its RPC. It is recommended using RESTCONF with
the controller-config loopback mountpoint, instead of using just NETCONF.
The NETCONF server for MD-SAL uses a standard MD-SAL API to act as an alternative
to RESTCONF. It is fully model driven to support any data and RPC supported by MD-SAL.
Install NETCONF northbound for MD-SAL by installing the ``odl-netconf-mdsal`` feature
in Karaf. Default binding port is 2830.

Default configuration can be found in the ``08-netconf-mdsal.xml`` file. This file
contains the configuration for all necessary dependencies, as well as a single SSH
endpoint starting on port 2830. There is also a TCP endpoint, which is disabled by
default. It is possible to start multiple endpoints simultaneously.

Verifying NETCONF Server
^^^^^^^^^^^^^^^^^^^^^^^^

After the NETCONF server is available, it can be examinedon the command line by
using the SSH tool. The server responds by sending a HELLO message, which can then
be used as a regular NETCONF server.

.. code-block:: none

   ssh admin@localhost -p 2830 -s netconf

Mounting NETCONF Server
^^^^^^^^^^^^^^^^^^^^^^^

Issue the following call to mount the NETCONF server for MD-SAL.

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://127.0.0.1:2830/restconf/operational/network-topology:network-topology/topology/controller-mdsal/``

**Method:** ``PUT``

After mounted successfully the NETCONF server, read its configuration by invoking the following:

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/operational/network-topology:network-topology/topology/topology-netconf/node/controller-mdsal/yang-ext:mount``

**Method:** ``GET``
