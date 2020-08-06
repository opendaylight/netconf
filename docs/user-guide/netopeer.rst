.. _netopeer:

NETCONF-Connector Configuration via Netopeer
============================================

Netopeer can be configured as an open-source NETCONF server. It can be used to test/explore NETCONF
southbound interfaces. For information on installing Netopeer, refer to `Set up Netopeer Server
<http://www.seguesoft.com/index.php/how-to-set-up-netopeer-server-to-use-with-netconfc>`_.

Installing Netopeer
-------------------

A Docker container is required to run Netopeer. Perform the following steps to install Docker and
to start a Netopeer image:

#. Refer to the `Orientation and setup <http://docs.docker.com/linux/step_one/>`_ page at the
   Docker Website to install Docker.

#. Issue the following command to start the Netopeer image:

   .. code-block:: none

      docker run --rm -t -p 1831:830 dockeruser/netopeer

#. Use the following ``ssh`` command to ensure Netopeer is running. Netopeer should send a HELLO message immediately.

   .. code-block:: none

      ssh root@localhost -p 1831 -s netconf

   The password is ``root``.

Mounting a Netopeer NETCONF Server
----------------------------------

After Netopeer is installed, both the ``odl-restconf-all`` and ``odl-netconf-connector-all``
will start, and Netopeer will be up-and-running in Docker.
Now, go to :ref:`create-a-netconf` to spawn a new NETCONF connector. However, change the
following in the payload:

* Change ``name`` to ``netopeer``.

* Use your username/password for your system credentials.

* Change ``ip`` to ``localhost``

* Change ``port`` to ``1831``.

After Netopeer is successfully mounted, its configuration can be read using RESTCONF by invoking
the following:

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

**URL:** ``http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/netopeer/yang-ext:mount/``

**Method:** GET

**Payload:** none
