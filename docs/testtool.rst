.. _testtool:

.. |ss| raw:: html

   <strike>

.. |se| raw:: html

   </strike>

NETCONF testtool
----------------

**NETCONF testtool is a set of standalone runnable jars that can:**

-  Simulate NETCONF devices (suitable for scale testing)

-  Stress/Performance test NETCONF devices

-  Stress/Performance test RESTCONF devices

These jars are part of OpenDaylight’s controller project and are built
from the NETCONF codebase in OpenDaylight.

.. tip::

    Download testtool from OpenDaylight Nexus at:
    https://nexus.opendaylight.org/content/repositories/public/org/opendaylight/netconf/netconf-testtool/1.1.0-Boron/

**Nexus contains 3 executable tools:**

-  executable.jar - device simulator

-  stress.client.tar.gz - NETCONF stress/performance measuring tool

-  perf-client.jar - RESTCONF stress/performance measuring tool

.. tip::

    Each executable tool provides help. Just invoke ``java -jar
    <name-of-the-tool.jar> --help``

NETCONF device simulator
~~~~~~~~~~~~~~~~~~~~~~~~

NETCONF testtool (or NETCONF device simulator) is a tool that

-  Simulates 1 or more NETCONF devices

-  Is suitable for scale, performance or crud testing

-  Uses core implementation of NETCONF server from OpenDaylight

-  Generates configuration files for controller so that the OpenDaylight
   distribution (Karaf) can easily connect to all simulated devices

-  Provides broad configuration options

-  Can start a fully fledged MD-SAL datastore

-  Supports notifications

Building testtool
^^^^^^^^^^^^^^^^^

1. Check out latest NETCONF repository from
   `git <https://git.opendaylight.org/gerrit/#/admin/projects/netconf>`__

2. Move into the ``opendaylight/netconf/tools/netconf-testtool/`` folder

3. Build testtool using the ``mvn clean install`` command

Downloading testtool
^^^^^^^^^^^^^^^^^^^^

Netconf-testtool is now part of default maven build profile for
controller and can be also downloaded from nexus. The executable jar for
testtool can be found at:
`nexus-artifacts <https://nexus.opendaylight.org/content/repositories/public/org/opendaylight/netconf/netconf-testtool/1.1.0-Boron/>`__

Running testtool
^^^^^^^^^^^^^^^^

1. After successfully building or downloading, move into the
   ``opendaylight/netconf/tools/netconf-testtool/target/`` folder and
   there is file ``netconf-testtool-1.1.0-SNAPSHOT-executable.jar`` (or
   if downloaded from nexus just take that jar file)

2. Execute this file using, e.g.:

   ::

       java -jar netconf-testtool-1.1.0-SNAPSHOT-executable.jar

   This execution runs the testtool with default for all parameters and
   you should see this log output from the testtool :

   ::

       10:31:08.206 [main] INFO  o.o.c.n.t.t.NetconfDeviceSimulator - Starting 1, SSH simulated devices starting on port 17830
       10:31:08.675 [main] INFO  o.o.c.n.t.t.NetconfDeviceSimulator - All simulated devices started successfully from port 17830 to 17830

Default Parameters
''''''''''''''''''

The default parameters for testtool are:

-  Use SSH

-  Run 1 simulated device

-  Device port is 17830

-  YANG modules used by device are only: ietf-netconf-monitoring,
   ietf-yang-types, ietf-inet-types (these modules are required for
   device in order to support NETCONF monitoring and are included in the
   netconf-testtool)

-  Connection timeout is set to 30 minutes (quite high, but when testing
   with 10000 devices it might take some time for all of them to fully
   establish a connection)

-  Debug level is set to false

-  No distribution is modified to connect automatically to the NETCONF
   testtool

Verifying testtool
^^^^^^^^^^^^^^^^^^

To verify that the simulated device is up and running, we can try to
connect to it using command line ssh tool. Execute this command to
connect to the device:

::

    ssh admin@localhost -p 17830 -s netconf

Just accept the server with yes (if required) and provide any password
(testtool accepts all users with all passwords). You should see the
hello message sent by simulated device.

Testtool help
^^^^^^^^^^^^^

::

    usage: netconf testtool [-h] [--edit-content EDIT-CONTENT] [--async-requests {true,false}] [--thread-amount THREAD-AMOUNT] [--throttle THROTTLE]
                            [--auth AUTH AUTH] [--controller-destination CONTROLLER-DESTINATION] [--device-count DEVICES-COUNT]
                            [--devices-per-port DEVICES-PER-PORT] [--schemas-dir SCHEMAS-DIR] [--notification-file NOTIFICATION-FILE]
                            [--initial-config-xml-file INITIAL-CONFIG-XML-FILE] [--starting-port STARTING-PORT]
                            [--generate-config-connection-timeout GENERATE-CONFIG-CONNECTION-TIMEOUT]
                            [--generate-config-address GENERATE-CONFIG-ADDRESS] [--generate-configs-batch-size GENERATE-CONFIGS-BATCH-SIZE]
                            [--distribution-folder DISTRO-FOLDER] [--ssh {true,false}] [--exi {true,false}] [--debug {true,false}]
                            [--md-sal {true,false}] [--time-out TIME-OUT] [-ip IP] [--thread-pool-size THREAD-POOL-SIZE] [--rpc-config RPC-CONFIG]

    netconf testtool

    named arguments:
      -h, --help             show this help message and exit
      --edit-content EDIT-CONTENT
      --async-requests {true,false}
      --thread-amount THREAD-AMOUNT
                             The number of threads to use for configuring devices.
      --throttle THROTTLE    Maximum amount of async requests that can be open at a time, with mutltiple threads this gets divided among all threads
      --auth AUTH AUTH       Username and password for HTTP basic authentication in order username password.
      --controller-destination CONTROLLER-DESTINATION
                             Ip address and port of controller. Must  be  in  following  format  <ip>:<port>  if  available it will be used for spawning
                             netconf   connectors    via    topology    configuration    as    a    part    of    URI.    Example    (http://<controller
                             destination>/restconf/config/network-topology:network-topology/topology/topology-netconf/node/<node-id>)otherwise  it  will
                             just start simulated devices and skip the execution of PUT requests
      --device-count DEVICES-COUNT
                             Number of simulated netconf devices to spin. This is the number of actual ports open for the devices.
      --devices-per-port DEVICES-PER-PORT
                             Amount of config files generated per port to spoof more devices than are actually running
      --schemas-dir SCHEMAS-DIR
                             Directory containing yang schemas to describe simulated devices.  Some  schemas  e.g. netconf monitoring and inet types are
                             included by default
      --notification-file NOTIFICATION-FILE
                             Xml file containing notifications that should be sent to clients after create subscription is called
      --initial-config-xml-file INITIAL-CONFIG-XML-FILE
                             Xml file containing initial simulatted configuration to be returned via get-config rpc
      --starting-port STARTING-PORT
                             First port for simulated device. Each other device will have previous+1 port number
      --generate-config-connection-timeout GENERATE-CONFIG-CONNECTION-TIMEOUT
                             Timeout to be generated in initial config files
      --generate-config-address GENERATE-CONFIG-ADDRESS
                             Address to be placed in generated configs
      --generate-configs-batch-size GENERATE-CONFIGS-BATCH-SIZE
                             Number of connector configs per generated file
      --distribution-folder DISTRO-FOLDER
                             Directory where the karaf distribution for controller is located
      --ssh {true,false}     Whether to use ssh for transport or just pure tcp
      --exi {true,false}     Whether to use exi to transport xml content
      --debug {true,false}   Whether to use debug log level instead of INFO
      --md-sal {true,false}  Whether to use md-sal datastore instead of default simulated datastore.
      --time-out TIME-OUT    the maximum time in seconds for executing each PUT request
      -ip IP                 Ip address which will be used for creating a socket  address.It  can  either  be a machine name, such as java.sun.com, or a
                             textual representation of its IP address.
      --thread-pool-size THREAD-POOL-SIZE
                             The number of threads to keep in the pool, when creating a device simulator. Even if they are idle.
      --rpc-config RPC-CONFIG
                             Rpc config file. It can be used to define custom rpc  behavior, or override the default one.Usable for testing buggy device
                             behavior.


Supported operations
^^^^^^^^^^^^^^^^^^^^

Testtool default simple datastore supported operations:

get-schema
    returns YANG schemas loaded from user specified directory,

edit-config
    always returns OK and stores the XML from the input in a local
    variable available for get-config and get RPC. Every edit-config
    replaces the previous data,

commit
    always returns OK, but does not actually commit the data,

get-config
    returns local XML stored by edit-config,

get
    returns local XML stored by edit-config with netconf-state subtree,
    but also supports filtering.

(un)lock
    returns always OK with no lock guarantee

create-subscription
    returns always OK and after the operation is triggered, provided
    NETCONF notifications (if any) are fed to the client. No filtering
    or stream recognition is supported.

Note: when operation="delete" is present in the payload for edit-config,
it will wipe its local store to simulate the removal of data.

When using the MD-SAL datastore testtool behaves more like normal
NETCONF server and is suitable for crud testing. create-subscription is
not supported when testtool is running with the MD-SAL datastore.

Notification support
^^^^^^^^^^^^^^^^^^^^

Testtool supports notifications via the --notification-file switch. To
trigger the notification feed, create-subscription operation has to be
invoked. The XML file provided should look like this example file:

::

    <?xml version='1.0' encoding='UTF-8' standalone='yes'?>
    <notifications>

    <!-- Notifications are processed in the order they are defined in XML -->

    <!-- Notification that is sent only once right after create-subscription is called -->
    <notification>
        <!-- Content of each notification entry must contain the entire notification with event time. Event time can be hardcoded, or generated by testtool if XXXX is set as eventtime in this XML -->
        <content><![CDATA[
            <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">
                <eventTime>2011-01-04T12:30:46</eventTime>
                <random-notification xmlns="http://www.opendaylight.org/netconf/event:1.0">
                    <random-content>single no delay</random-content>
                </random-notification>
            </notification>
        ]]></content>
    </notification>

    <!-- Repeated Notification that is sent 5 times with 2 second delay inbetween -->
    <notification>
        <!-- Delay in seconds from previous notification -->
        <delay>2</delay>
        <!-- Number of times this notification should be repeated -->
        <times>5</times>
        <content><![CDATA[
            <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">
                <eventTime>XXXX</eventTime>
                <random-notification xmlns="http://www.opendaylight.org/netconf/event:1.0">
                    <random-content>scheduled 5 times 10 seconds each</random-content>
                </random-notification>
            </notification>
        ]]></content>
    </notification>

    <!-- Single notification that is sent only once right after the previous notification -->
    <notification>
        <delay>2</delay>
        <content><![CDATA[
            <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">
                <eventTime>XXXX</eventTime>
                <random-notification xmlns="http://www.opendaylight.org/netconf/event:1.0">
                    <random-content>single with delay</random-content>
                </random-notification>
            </notification>
        ]]></content>
    </notification>

    </notifications>

Connecting testtool with controller Karaf distribution
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Auto connect to OpenDaylight
''''''''''''''''''''''''''''

It is possible to make OpenDaylight auto connect to the simulated
devices spawned by testtool (so user does not have to post a
configuration for every NETCONF connector via RESTCONF). The testtool is
able to modify the OpenDaylight distribution to auto connect to the
simulated devices after feature ``odl-netconf-connector-all`` is
installed. When running testtool, issue this command (just point the
testool to the distribution:

::

    java -jar netconf-testtool-1.1.0-SNAPSHOT-executable.jar --device-count 10 --distribution-folder ~/distribution-karaf-0.4.0-SNAPSHOT/ --debug true

With the distribution-folder parameter, the testtool will modify the
distribution to include configuration for netconf-connector to connect
to all simulated devices. So there is no need to spawn
netconf-connectors via RESTCONF.

Running testtool and OpenDaylight on different machines
'''''''''''''''''''''''''''''''''''''''''''''''''''''''

The testtool binds by default to 0.0.0.0 so it should be accessible from
remote machines. However you need to set the parameter
"generate-config-address" (when using autoconnect) to the address of
machine where testtool will be run so OpenDaylight can connect. The
default value is localhost.

Executing operations via RESTCONF on a mounted simulated device
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Simulated devices support basic RPCs for editing their config. This part
shows how to edit data for simulated device via RESTCONF.

Test YANG schema
''''''''''''''''

The controller and RESTCONF assume that the data that can be manipulated
for mounted device is described by a YANG schema. For demonstration, we
will define a simple YANG model:

::

    module test {
        yang-version 1;
        namespace "urn:opendaylight:test";
        prefix "tt";

        revision "2014-10-17";


       container cont {

            leaf l {
                type string;
            }
       }
    }

Save this schema in file called test@2014-10-17.yang and store it a
directory called test-schemas/, e.g., your home folder.

Editing data for simulated device
'''''''''''''''''''''''''''''''''

-  Start the device with following command:

   ::

       java -jar netconf-testtool-1.1.0-SNAPSHOT-executable.jar --device-count 10 --distribution-folder ~/distribution-karaf-0.4.0-SNAPSHOT/ --debug true --schemas-dir ~/test-schemas/

-  Start OpenDaylight

-  Install odl-netconf-connector-all feature

-  Install odl-restconf feature

-  Check that you can see config data for simulated device by executing
   GET request to

   ::

       http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/17830-sim-device/yang-ext:mount/

-  The data should be just and empty data container

-  Now execute edit-config request by executing a POST request to:

   ::

       http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/17830-sim-device/yang-ext:mount

   with headers:

   ::

       Accept application/xml
       Content-Type application/xml

   and payload:

   ::

       <cont xmlns="urn:opendaylight:test">
         <l>Content</l>
       </cont>

-  Check that you can see modified config data for simulated device by
   executing GET request to

   ::

       http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/17830-sim-device/yang-ext:mount/

-  Check that you can see the same modified data in operational for
   simulated device by executing GET request to

   ::

       http://localhost:8181/restconf/operational/network-topology:network-topology/topology/topology-netconf/node/17830-sim-device/yang-ext:mount/

.. warning::

    Data will be mirrored in operational datastore only when using the
    default simple datastore.


Testing User defined RPC
^^^^^^^^^^^^^^^^^^^^^^^^

The NETCONF test-tool allows using custom RPC. Custom RPC needs to be defined in yang model provide to test-tool along
with parameter ``--schemas-dir``.

The input and output of the custom RPC should be provided with ``--rpc-config`` parameter as a path to the file containing
definition of input and output. The format of the custom RPC file is xml as shown below.

Start the device with following command:

::

    java -jar netconf/tools/netconf-testtool/target/netconf-testtool-1.7.0-SNAPSHOT-executable.jar --schemas-dir ~/test-schemas/ --rpc-config ~/tmp/customrpc.xml --debug=true

Example YANG model file:

::

    module example-ops {
         namespace "urn:example-ops:reboot";
         prefix "ops";

        import ietf-yang-types {
        prefix "yang";
         }


         revision "2016-07-07" {
           description "Initial version.";
           reference "example document.";
         }


         rpc reboot {
           description "Reboot operation.";
           input {
             leaf delay {
               type uint32;
               units "seconds";
               default 0;
               description
                 "Delay in seconds.";
             }
             leaf message {
               type string;
               description
                 "Log message.";
             }
           }
         }
       }


Example payload (RPC config file customrpc.xml):

::

    <rpcs>
      <rpc>
        <input>
          <reboot xmlns="urn:example-ops:reboot">
            <delay>300</delay>
            <message>message</message>
          </reboot>
        </input>
        <output>
          <rpc-reply xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
            <ok/>
          </rpc-reply>
        </output>
      </rpc>
    </rpcs>



Example of use:

::

    POST http://localhost:8181/restconf/operations/network-topology:network-topology/topology/topology-netconf/node/new-netconf-device/yang-ext:mount/example-ops:get-reboot-info

If successful the command will return code 200.



.. note::

    A working example of user defined RPC can be found in TestToolTest.java class of the tools[netconf-testtool] project.


Known problems
^^^^^^^^^^^^^^

Slow creation of devices on virtual machines
''''''''''''''''''''''''''''''''''''''''''''

When testtool seems to take unusually long time to create the devices
use this flag when running it:

::

    -Dorg.apache.sshd.registerBouncyCastle=false

Too many files open
'''''''''''''''''''

When testtool or OpenDaylight starts to fail with TooManyFilesOpen
exception, you need to increase the limit of open files in your OS. To
find out the limit in linux execute:

::

    ulimit -a

Example sufficient configuration in linux:

::

    core file size          (blocks, -c) 0
    data seg size           (kbytes, -d) unlimited
    scheduling priority             (-e) 0
    file size               (blocks, -f) unlimited
    pending signals                 (-i) 63338
    max locked memory       (kbytes, -l) 64
    max memory size         (kbytes, -m) unlimited
    open files                      (-n) 500000
    pipe size            (512 bytes, -p) 8
    POSIX message queues     (bytes, -q) 819200
    real-time priority              (-r) 0
    stack size              (kbytes, -s) 8192
    cpu time               (seconds, -t) unlimited
    max user processes              (-u) 63338
    virtual memory          (kbytes, -v) unlimited
    file locks                      (-x) unlimited

To set these limits edit file: /etc/security/limits.conf, for example:

::

    *         hard    nofile      500000
    *         soft    nofile      500000
    root      hard    nofile      500000
    root      soft    nofile      500000

"Killed"
''''''''

The testtool might end unexpectedly with a simple message: "Killed".
This means that the OS killed the tool due to too much memory consumed
or too many threads spawned. To find out the reason on linux you can use
following command:

::

    dmesg | egrep -i -B100 'killed process'

Also take a look at this file: /proc/sys/kernel/threads-max. It limits
the number of threads spawned by a process. Sufficient (but probably
much more than enough) value is, e.g., 126676