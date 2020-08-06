.. _northbound:

=========================
Northbound NETCONF Server
=========================

A northbound server can act as a northbound interface for NETCONF. There are two types of NETCONF
servers: :ref:`config-subsystem` and :ref:`config-mdsal`.

.. _config-subsystem:
   
NETCONF Server for Config-Subsystem
-----------------------------------

The NETCONF server is the primary interface for config-subsystem. It listens on port 1830 to allow
users to interact with config-subsystem in a standardized NETCONF manner. The config-server allows
users to create, reconfigure, and delete modules or applications.
Config-subsystem supports the following RFCs:

* `RFC-6241 <https://tools.ietf.org/html/rfc6241>`_
* `RFC-5277 <https://tools.ietf.org/html/rfc5277>`_
* `RFC-6470 <https://tools.ietf.org/html/rfc6470>`_
* `RFC-6022 <https://tools.ietf.org/html/rfc6022>`_

.. note:: For most users, it is recommended using RESTCONF with the controller-config
          loopback mountpoint, instead of using pure NETCONF.

.. _config-mdsal:

NETCONF Server for MD-SAL
-------------------------

This NETCONF server is a generic interface to MD-SAL. It listens on port 2830, as well as use
standard MD-SAL APIs to serves as an alternative to RESTCONF. MD-SAL is fully-model driven to
support any data and RPC that are supported by MD-SAL. 
MD-SAL supports the following RFCs: 

* `RFC-6241 <https://tools.ietf.org/html/rfc6241>`_
* `RFC-6022 <https://tools.ietf.org/html/rfc6022>`_
* `draft-ietf-netconf-yang-library-06 <https://tools.ietf.org/html/draft-ietf-netconf-yang-library-06>`_

Configuration
^^^^^^^^^^^^^

Install NETCONF northbound for MD-SAL by installing the ``odl-netconf-mdsal`` feature
in Karaf. Default configuration can be found in the ``08-netconf-mdsal.xml`` file. This file
contains the configuration for all necessary dependencies, as well as a single SSH
endpoint starting on port 2830. There is also a TCP endpoint, which is disabled by
default. It is possible to start multiple endpoints simultaneously.

Verifying NETCONF Server
------------------------

After the NETCONF server is available, it can be examined on the command line by
using the SSH tool. The server responds by sending a HELLO message, which can then
be used as a regular NETCONF server.

.. code-block:: none

   ssh admin@localhost -p 2830 -s netconf

Configuring a Northbound NETCONF-Connector
------------------------------------------

To configure a northbound NETCONF-Connector via MD-SAL, refer to :ref:`create-a-netconf` to perform
this operation; however, change the following:

* ``ip`` to ``127.0.0.1`` 
* ``port`` to ``2830``
* ``name`` to ``controller-mdsal``


Mounting a NETCONF Server
-------------------------

Read the MD-SAL’s datastore over RESTCONF via NETCONF by issuing the following call:

**Headers:**

* **Content-type:** ``application/xml``
* **Accept:** ``application/xml``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/operational/network-topology:network-topology/topology/topology-netconf/node/controller-mdsal/yang-ext:mount``

**Method:** ``GET``

Measuring Tools
---------------

It is recommended and important to thoroughly test the functionality of a NETCONF server. Stress management
will test the plane with many concurrent NETCONF sessions to assess the impact to regular control plane and
data plane operation of a network device. Refer to `NETCONF Test Tools <https://docs.opendaylight.org/projects/netconf/en/latest/testtool.html>`_
for more information on these test tools.
The important aspects of a NETCONF Server validation can be classified in the following categories:

.. list-table:: Measuring Tools
   :widths: 20 60
   :header-rows: 1

   * - **Tool**
     - **Description**
   * - NETCONF
     - This stress/performance measuring tool is for NETCONF clients. They are designed to
       put a NETCONF server under a heavyload of RPCs to measure the time until a configurable
       amount of them are processed.
   * - RESTCONF
     - This stress/performance measuring tool is similar to the NETCONF stress tool, except it uses RESTCONF.
