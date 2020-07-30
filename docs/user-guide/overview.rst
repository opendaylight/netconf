Overview
========

The Network Configuration (NETCONF) protocol is a network management protocol developed and
standardized by the Internet Engineering Task Force (IETF). NETCONF is designed to install,
update, and delete the configurations of network devices. Operating on top of the Remote
Procedure Call (RPC) layer using XML encoding, NETCONF provides a set of operational tools
that can be used to edit and query configuration data of devices.
NETCONF can operate either as a :ref:`southbound` or as a :ref:`northbound`.

NETCONF Layers
--------------

The following table describes the conceptional layers featured on the NETCONF
plugin:

.. list-table:: NETCONF Layers
   :widths: 20 60
   :header-rows: 1

   * - **Layer**
     - **Description**
   * - **Content layer**
     - This layer includes both configuration and notification data.
   * - **Operations layer**
     - This layer defines a set of base-protocol operations to retrieve
       and edit config data.
   * - **Messages layer**
     - This layer is a mechanism that encodes RPCs and notifications.
   * - **Secure Transport layer**
     - This layer provides a secure and reliable for messaging between
       clients and servers.

.. _southbound:

Southbound Plugin
-----------------

As a southbound plugin (NETCONF-connector) NETCONF can connect to remote NETCONF
devices to expose their configuration/operational datastores, RPCs, and
notifications as an MD-SAL mount point. Mount points allow apps and remote
users to interact with the mounted devices over RESTCONF. The connector
supports these added RPCs:

* `RFC-5277 <http://tools.ietf.org/html/rfc5277>`_
* `RFC-6022 <http://tools.ietf.org/html/rfc6022>`_
* `draft-ietf-netconf-yang-library-06 <https://tools.ietf.org/html/draft-ietf-netconf-yang-library-06>`_

By using the YANG modeling language, the NETCONF-connector is fully model-driven.
Thus, in addition to the above RFCs, it also supports any data/RPC/notifications that
are described by the YANG model implemented in devices. To activate the NETCONF
southbound plugin, install the ``odl-netconf-connector-all`` Karaf feature. Refer to
:ref:create-added-netconf.

NETCONF-Connector
-----------------

Users can configure the NETCONF-connector using either NETCONF or RESTCONF; however,
this guide focuses on using RESTCONF. In addition, there are two different
endpoints related to the RESTCONF protocol:

* ``http://localhost:8181/restconf`` is related to `draft-bierman-netconf-restconf-02
  <https://tools.ietf.org/html/draft-bierman-netconf-restconf-02>`_. It can be activated
  by installing the ``odl-restconf-nb-bierman02`` Karaf feature.

* ``http://localhost:8181/rests`` is related to `RFC-8040 <http://tools.ietf.org/html/rfc8040>`_.
  It can be activated by installing the ``odl-restconf-nb-rfc8040`` Karaf feature.


For `RFC-8040 <http://tools.ietf.org/html/rfc8040>`_, the resources to configure and
operate datastores start with ``/rests/data/``, for example:

* ``GET http://localhost:8181/rests/data/network-topology:network-topology`` with
  response of both datastores; that is, configuration and operational.
* ``GET http://localhost:8181/rests/data/network-topology:network-topology?content=config`` for configuration datastore.
* ``GET http://localhost:8181/rests/data/network-topology:network-topology?content=nonconfig`` for operational datastore.

Default Configuration
^^^^^^^^^^^^^^^^^^^^^

The NETCONF default configuration has all necessary dependencies (such as, ``01-netconf.xml``), as well
as a single instance of NETCONF-connector (such as, ``99-netconf-connector.xml``) called ``controller-config``,
which connects itself to the NETCONF northbound in a loopback fashion. The connector mounts the
NETCONF server for config-subsystem to enable RESTCONF protocol for config-subsystem.
