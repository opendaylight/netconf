NETCONF Clustering
------------------

Clustering support for NETCONF is done by installing the ``odl-netconf-clustered-topology``
feature. When a new clustered connector is configured for NETCONF (i.e., when a new
NETCONF-enabled device is mounted), configuration is distributed among the member
nodes and a NETCONF connector is created on each node. From these nodes, a master
is chosen to handle the schema download, as well as to communicate with each device.
When the master node goes down, another node in the cluster takes ownership of the
devices; thus, providing high availability. In addition, since each node in the
cluster takes responsibility for subsets of the connected NETCONF devices,
load balancing is also achieved.

Users can also use the ``odl-netconf-clustered-topology`` feature in a single-node
scenario. However, any code that uses **akka** will be used, so for a scenario where
only a single node is used, ``odl-netconf-topology`` might be preferred.

.. warning:: The ``odl-netconf-topology`` and ``odl-netconf-clustered-topology``
             features are considered incompatible since they both manage the same
             space in the datastore. This would issue conflicting writes when
             installed together.
