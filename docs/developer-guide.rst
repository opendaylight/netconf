.. _netconf-dev-guide:

NETCONF Developer Guide
=======================

.. note::

    Reading the NETCONF section in the User Guide is likely useful as it
    contains an overview of NETCONF in OpenDaylight and a how-to for
    spawning and configuring NETCONF connectors.

This chapter is recommended for application developers who want to
interact with mounted NETCONF devices from their application code. It
tries to demonstrate all the use cases from user guide with RESTCONF but
now from the code level. One important difference would be the
demonstration of NETCONF notifications and notification listeners. The
notifications were not shown using RESTCONF because **RESTCONF does not
support notifications from mounted NETCONF devices.**

.. note::

    It may also be useful to read the generic `OpenDaylight MD-SAL app
    development
    tutorial <https://wiki-archive.opendaylight.org/view/OpenDaylight_Controller:MD-SAL:MD-SAL_App_Tutorial>`__
    before diving into this chapter. This guide assumes awareness of
    basic OpenDaylight application development.

Sample app overview
-------------------

All the examples presented here are implemented by a sample OpenDaylight
application called **ncmount** in the ``coretutorials`` OpenDaylight
project. It can be found on the github mirror of OpenDaylight’s
repositories:

-  https://github.com/opendaylight/coretutorials/tree/master/ncmount

or checked out from the official OpenDaylight repository:

-  https://git.opendaylight.org/gerrit/admin/repos/coretutorials

**The application was built using the** `project startup maven
archetype <https://wiki-archive.opendaylight.org/view/OpenDaylight_Controller:MD-SAL:Startup_Project_Archetype>`__
**and demonstrates how to:**

-  preconfigure connectors to NETCONF devices

-  retrieve MountPointService (registry of available mount points)

-  listen and react to changing connection state of netconf-connector

-  add custom device YANG models to the app and work with them

-  read data from device in binding aware format (generated java APIs
   from provided YANG models)

-  write data into device in binding aware format

-  trigger and listen to NETCONF notifications in binding aware format

Detailed information about the structure of the application can be found
at:
https://wiki-archive.opendaylight.org/view/Controller_Core_Functionality_Tutorials:Tutorials:Netconf_Mount

.. note::

    The code in ncmount is fully **binding aware** (works with generated
    java APIs from provided YANG models). However it is also possible to
    perform the same operations in **binding independent** manner.

NcmountProvider
~~~~~~~~~~~~~~~

The NcmountProvider class (found in NcmountProvider.java) is the central
point of the ncmount application and all the application logic is
contained there. The following sections will detail its most interesting
pieces.

Retrieve MountPointService
^^^^^^^^^^^^^^^^^^^^^^^^^^

The MountPointService is a central registry of all available mount
points in OpenDaylight. It is just another MD-SAL service and is
available from the ``session`` attribute passed by
``onSessionInitiated`` callback:

::

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("NcmountProvider Session Initiated");

        // Get references to the data broker and mount service
        this.mountService = session.getSALService(MountPointService.class);

        ...

        }
    }

Listen for connection state changes
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

It is important to know when a mount point appears, when it is fully
connected and when it is disconnected or removed. The exact states of a
mount point are:

-  Connected

-  Connecting

-  Unable to connect

To receive this kind of information, an application has to register
itself as a notification listener for the preconfigured netconf-topology
subtree in MD-SAL’s datastore. This can be performed in the
``onSessionInitiated`` callback as well:

::

    @Override
    public void onSessionInitiated(ProviderContext session) {

        ...

        this.dataBroker = session.getSALService(DataBroker.class);

        // Register ourselves as the REST API RPC implementation
        this.rpcReg = session.addRpcImplementation(NcmountService.class, this);

        // Register ourselves as data change listener for changes on Netconf
        // nodes. Netconf nodes are accessed via "Netconf Topology" - a special
        // topology that is created by the system infrastructure. It contains
        // all Netconf nodes the Netconf connector knows about. NETCONF_TOPO_IID
        // is equivalent to the following URL:
        // .../restconf/operational/network-topology:network-topology/topology/topology-netconf
        if (dataBroker != null) {
            this.dclReg = dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    NETCONF_TOPO_IID.child(Node.class),
                    this,
                    DataChangeScope.SUBTREE);
        }
    }

The implementation of the callback from MD-SAL when the data change can
be found in the
``onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject>
change)`` callback of `NcmountProvider
class <https://github.com/opendaylight/coretutorials/blob/master/ncmount/impl/src/main/java/ncmount/impl/NcmountProvider.java>`__.

Reading data from the device
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The first step when trying to interact with the device is to get the
exact mount point instance (identified by an instance identifier) from
the MountPointService:

::

    @Override
    public Future<RpcResult<ShowNodeOutput>> showNode(ShowNodeInput input) {
        LOG.info("showNode called, input {}", input);

        // Get the mount point for the specified node
        // Equivalent to '.../restconf/<config | operational>/opendaylight-inventory:nodes/node/<node-name>/yang-ext:mount/'
        // Note that we can read both config and operational data from the same
        // mount point
        final Optional<MountPoint> xrNodeOptional = mountService.getMountPoint(NETCONF_TOPO_IID
                .child(Node.class, new NodeKey(new NodeId(input.getNodeName()))));

        Preconditions.checkArgument(xrNodeOptional.isPresent(),
                "Unable to locate mountpoint: %s, not mounted yet or not configured",
                input.getNodeName());
        final MountPoint xrNode = xrNodeOptional.orElseThrow();

        ....
    }

.. note::

    The triggering method in this case is called ``showNode``. It is a
    YANG-defined RPC and NcmountProvider serves as an MD-SAL RPC
    implementation among other things. This means that ``showNode`` an
    be triggered using RESTCONF.

The next step is to retrieve an instance of the ``DataBroker`` API from
the mount point and start a read transaction:

::

    @Override
    public Future<RpcResult<ShowNodeOutput>> showNode(ShowNodeInput input) {

        ...

        // Get the DataBroker for the mounted node
        final DataBroker xrNodeBroker = xrNode.getService(DataBroker.class).orElseThrow();
        // Start a new read only transaction that we will use to read data
        // from the device
        final ReadOnlyTransaction xrNodeReadTx = xrNodeBroker.newReadOnlyTransaction();

        ...
    }

Finally, it is possible to perform the read operation:

::

    @Override
    public Future<RpcResult<ShowNodeOutput>> showNode(ShowNodeInput input) {

        ...

        InstanceIdentifier<InterfaceConfigurations> iid =
                InstanceIdentifier.create(InterfaceConfigurations.class);

        Optional<InterfaceConfigurations> ifConfig;
        try {
            // Read from a transaction is asynchronous, but a simple
            // get/checkedGet makes the call synchronous
            ifConfig = xrNodeReadTx.read(LogicalDatastoreType.CONFIGURATION, iid).checkedGet();
        } catch (ReadFailedException e) {
            throw new IllegalStateException("Unexpected error reading data from " + input.getNodeName(), e);
        }

        ...
    }

The instance identifier is used here again to specify a subtree to read
from the device. At this point application can process the data as it
sees fit. The ncmount app transforms the data into its own format and
returns it from ``showNode``.

.. note::

    More information can be found in the source code of ncmount sample
    app + on wiki:
    https://wiki-archive.opendaylight.org/view/Controller_Core_Functionality_Tutorials:Tutorials:Netconf_Mount

Reading selected fields from device
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Using NETCONF DOM API it is also possible to read only selected fields
from NETCONF device. NETCONF mountpoint exposes 2 services that provides
this functionality:

a. **NetconfDataTreeService** - It provides overloaded methods 'get'
   (operational data) and 'getConfig' (configuration data)
   with 'List<YangInstanceIdentifier> fields' parameters. This service
   should be used if transaction properties are not required.
b. **NetconfDOMDataBrokerFieldsExtension** - It implements DOMDataBroker
   interface - provides the same transaction functionality plus method
   for reading of data with selected fields: read(LogicalDatastoreType,
   YangInstanceIdentifier, List<YangInstanceIdentifier>).
   Instance of NetconfDOMDataBrokerFieldsExtension can be obtained
   using 'getExtensions()' method on the DOMDataBroker instance.

'List<YangInstanceIdentifier> fields' parameter specifies list of paths
that are read from device using subtree filtering. These paths must be
relative to parent path - returned data follows schema of the last path
argument of parent path.

**Mechanics**

* parent path: /a/b/c
* fields: [/x/y; /w/z]

NETCONF server will read data on following paths:
* /a/b/c/x/y
* /a/b/c/w/z

And place read data under /a/b/c path - thus returned data will have
following structure:

.. code-block::

    c: {
        x: {
            y: {
                // data ...
            }
        }
        w: {
            z: {
                // data ...
            }
        }
    }

From the view of DOM API, YangInstanceIdentifier (first parameter) represents
the parent path, and List<YangInstanceIdentifier> represents list of selected
fields.

**Example: using NetconfDataTreeService**

The following method demonstrates reading of 2 fields under parent list 'l1':

* parent path: /c1/l1
* fields (leaves 'x1' and 'x2'): [/c2/x1, /c2/l2/x2]

Result will contain whole MapNode with possibly multiple MapEntryNode-s that
contain only keys of list elements and selected fields.

.. code-block:: java

    public void readData(final DOMMountPoint mountPoint) {
        final NetconfDataTreeService dataTreeService = mountPoint.getService(NetconfDataTreeService.class).orElseThrow();

        final YangInstanceIdentifier parentPath = YangInstanceIdentifier.builder()
                .node(CONTAINER_C1_NI)   // container 'c1' (root element)
                .node(LIST_L1_NI)        // list 'l1' placed under container 'c1'
                .build();
        final YangInstanceIdentifier leafX1Field = YangInstanceIdentifier.builder()
                .node(CONTAINER_C2_NI)   // container 'c2' placed under list 'l1'
                .node(LEAF_X1_NI)        // leaf 'x1' placed under container 'c2'
                .build();
        final YangInstanceIdentifier leafX2Field = YangInstanceIdentifier.builder()
                .node(CONTAINER_C2_NI)   // container 'c2' placed under list 'l1'
                .node(NESTED_LIST_L2_NI) // list 'l2' placed under container 'c2'
                .node(LEAF_X2_NI)        // leaf 'x2' placed under list 'l2'
                .build();

        final ListenableFuture<Optional<NormalizedNode<?, ?>>> config = dataTreeService.getConfig(
                parentPath, Lists.newArrayList(leafX1Field, leafX2Field));

        Futures.addCallback(config, new FutureCallback<Optional<NormalizedNode<?, ?>>>() {
            @Override
            public void onSuccess(@Nullable final Optional<NormalizedNode<?, ?>> normalizedNode) {
                normalizedNode.ifPresent(node -> LOG.info("Read data: {}", NormalizedNodes.toStringTree(node)));
                /*
                We expect data with following structure:
                l1: [
                    {
                        key-l1: "k1",
                        c2: {
                            x1: 10,
                            l2: [
                                {
                                    key-l2: 1,
                                    x2: "foo"
                                },
                                ...
                            ]
                        }
                    },
                    ...
                ]
                */
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("Failed to read data: {}", parentPath, throwable);
            }
        });
    }

**Example: using NetconfDOMDataBrokerFieldsExtension**

The following method demonstrates reading of 2 fields under parent
container 'c1':

* parent path: /c1
* fields (leaf-list 'll1 'and container 'c2'): [/ll1, /l1=k1/c2]

Result will contain ContainerNode with identifier 'c1' and children
nodes (if they are present) of type LeafSetNode and MapNode.

.. code-block:: java

    public void readData(final DOMMountPoint mountPoint) {
        final DOMDataBroker domDataBroker = mountPoint.getService(DOMDataBroker.class).orElseThrow();
        final NetconfDOMDataBrokerFieldsExtension domFieldsDataBroker = domDataBroker.getExtensions().getInstance(
                NetconfDOMDataBrokerFieldsExtension.class);

        final YangInstanceIdentifier parentPath = YangInstanceIdentifier.builder()
                .node(CONTAINER_C1_NI)   // container 'c1' (root element)
                .build();
        final YangInstanceIdentifier ll1Field = YangInstanceIdentifier.builder()
                .node(LEAF_LIST_LL1_NI)  // leaf-list 'll1' placed under container 'c1'
                .build();
        final YangInstanceIdentifier c2Field = YangInstanceIdentifier.builder()
                .node(LIST_L1_NI)        // list 'l1' placed under container 'c1'
                .nodeWithKey(LIST_L1_QN, LIST_L1_KEY_QN, "k1") // specific list entry with key value 'k1'
                .node(CONTAINER_C2_NI)   // container 'c2' placed under container 'c1'
                .build();

        final FluentFuture<Optional<NormalizedNode<?, ?>>> data;
        try (NetconfDOMFieldsReadTransaction roTx = domFieldsDataBroker.newReadOnlyTransaction()) {
           data = roTx.read(LogicalDatastoreType.CONFIGURATION, parentPath, Lists.newArrayList(ll1Field, c2Field));
        }

        data.addCallback(new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable final Optional<NormalizedNode<?, ?>> normalizedNode) {
                normalizedNode.ifPresent(node -> LOG.info("Read data: {}", NormalizedNodes.toStringTree(node)));
                /*
                We expect data with following structure:
                c1: {
                    ll1: [...],
                    l1: [
                        {
                            l1-key: "k1",
                            c2: {
                                // data ...
                            }
                        }
                    ]
                }
                */
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("Failed to read data: {}", parentPath, throwable);
            }
        }, MoreExecutors.directExecutor());
    }
