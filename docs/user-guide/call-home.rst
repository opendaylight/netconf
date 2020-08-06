.. _netconf-call-home:

=================
NETCONF Call-Home
=================

The NETCONF Call-Home enables NETCONF to initiate a secure connection to a NETCONF-enabled device.
It is defined in `RFC 8071 <https://tools.ietf.org/html/rfc8071>`_. and is installed in Karaf when
installing the ``odl-netconf-callhome-ssh`` feature. Use Netopeer to test the Call-Home functionality.
Refer to `Netopeer Call-Home <https://github.com/CESNET/netopeer/wiki/CallHome>`_ to learn how to enable
call-home on Netopeer.

Northbound Call-Home API
------------------------

The northbound Call-Home API is used for administering the Call-Home server. The Call-Home server allows
user to configure global credentials, which will be used for devices that do not have device-specific
credentials configured. This is done by creating a ``/odl-netconf-callhome-server:netconf-callhome-server/global/credentials``, with username and passwords specified.
Issue the following command to configure global username and password:

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

**URL:** ``/restconf/config/odl-netconf-callhome-server:netconf-callhome-server/global/credentials HTTP/1.1``

**Method:** ``PUT``

**Payload:**

.. code-block:: none

   {
    "credentials":
    {
    "username": "example",
    "passwords": [ "first-password-to-try", "second-password-to-try" ]
    }
   }

Configuring an SSH Server
-------------------------

Users can configure to accept any SSH server key using global credentials. By default,
the NETCONF Call-Home Server accepts only incoming connections from allowed devices on
``/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices``. To allow all
incoming connections, set the ``accept-all-ssh-keys`` to **true** in the
``/odl-netconf-callhome-server:netconf-callhome-server/global`` folder.
The name of this devices in NETCONF-topology will be in the ``IP-address:port``.
To name a device, refer to :ref:`device-spec-config`.

Allowing an Unknown Devices to Connect
--------------------------------------

This debug feature should not be used in production since this is an obvious security issue.
In addition, this also causes the Call-Home Server to drastically increase its output to the log.
Issue the following command to allow an unknown device to connect:

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

**URL:** ``/restconf/config/odl-netconf-callhome-server:netconf-callhome-server/global HTTP/1.1``

**Method:** ``POST``

**Payload:**

.. code-block:: none

   {
     "global": {
      "accept-all-ssh-keys": "true"
     }
   }

.. _device-spec-config:

Device-Specific Configuration
-----------------------------

To allow device and configuring name, the NETCONF Call-Home Server uses device provided
by an SSH server key (host key) to identify devices. The pairing of name and server key is
configured in ``/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices``.
This list is colloquially called a whitelist.

If the Call-Home Server finds the SSH host key in the whitelist, it continues to negotiate
a NETCONF connection over an SSH session. If the SSH host key is not found, the connection
between the Call-Home server and the device is dropped immediately. In either case, the device
that connects to the Call home server leaves a record of its presence in the operational store.
The following is an example of how to configure a device:

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

**URL:** ``/restconf/config/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices/device/example HTTP/1.1``

**Method:** ``PUT``

**Payload:**

.. code-block:: none

   {
    "device": {
    "unique-id": "example",
    "ssh-host-key": "AAAAB3NzaC1yc2EAAAADAQABAAABAQDHoH1jMjltOJnCt999uaSfc48ySutaD3ISJ9fSECe1Spdq9o9mxj0kBTTTq+2V8hPspuW75DNgN+V/rgJeoUewWwCAasRx9X4eTcRrJrwOQKzb5Fk+UKgQmenZ5uhLAefi2qXX/agFCtZi99vw+jHXZStfHm9TZCAf2zi+HIBzoVksSNJD0VvPo66EAvLn5qKWQD4AdpQQbKqXRf5/W8diPySbYdvOP2/7HFhDukW8yV/7ZtcywFUIu3gdXsrzwMnTqnATSLPPuckoi0V2jd8dQvEcu1DY+rRqmqu0tEkFBurlRZDf1yhNzq5xWY3OXcjgDGN+RxwuWQK3cRimcosH"
    }
   }

Configuring Device with Device-specific Credentials
---------------------------------------------------

Call-Home Server also allows to configure credentials per device basis.
This is done by introducing credentials container into device-specific
configuration. Format is same as in global credentials.
Issue the following command to configure device with credentials:

**Headers:**

* **Content-type:** ``application/json``
* **Accept:** ``application/json``
* **Authentication:** ``admin:admin``

* **URL:** ``/restconf/config/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices/device/example HTTP/1.1``

**Method:** ``PUT``

**Payload:**

.. code-block:: none

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

Operational Status
------------------

Once an entry is made into the config side of “allowed-devices," the Call-Home Server will populate a
corresponding operational device that is the same as the config device but has an added status.
By default, this status is DISCONNECTED. Once a device calls home, this status will change to one of
the following:

.. list-table:: Operational Status
   :widths: 20 50
   :header-rows: 1

   * - **Status**
     - **Description**

   * - **CONNECTED**
     - Device is currently connected and the NETCONF mount is available for network management.
   * - **FAILED_AUTH_FAILURE**
     - The last attempted connection was unsuccessful because the Call-Home Server was unable to
       provide the acceptable credentials of the device. The device is also disconnected and not
       available for network management.
   * - **FAILED_NOT_ALLOWED**
     - The last attempted connection was unsuccessful because the device was not recognized as an
       acceptable device. The device is also disconnected and not available for network management.
   * - **FAILED**
     - The last attempted connection was unsuccessful for a reason other than not allowed to connect
       or incorrect client credentials. The device is also disconnected and not available for network management.
   * - **DISCONNECTED**
     - The device is currently disconnected.

Southbound Call-Home API
------------------------

The Call-Home Server listens for incoming TCP connections and assumes that the other side of the
connection is a device calling home via a NETCONF connection with SSH for management. By default,
the server uses port 6666, which can be configured via a blueprint configuration file.
The device must initiate the connection and the server will not try to re-establish the connection
when dropped. By requirement, the server cannot assume it has connectivity to the device due to NAT
or firewalls, among others.

