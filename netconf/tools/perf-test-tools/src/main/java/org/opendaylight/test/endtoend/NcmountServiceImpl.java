/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.test.endtoend;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.MountPoint;
import org.opendaylight.mdsal.binding.api.MountPointService;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.RouterStatic;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.address.family.AddressFamilyBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.address.family.address.family.Vrfipv4Builder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.router._static.Vrfs;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.router._static.vrfs.Vrf;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.router._static.vrfs.VrfBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.router._static.vrfs.VrfKey;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.prefix.table.VrfPrefixes;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.prefix.table.VrfPrefixesBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.prefix.table.vrf.prefixes.VrfPrefix;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.prefix.table.vrf.prefixes.VrfPrefixBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.prefix.table.vrf.prefixes.VrfPrefixKey;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.route.VrfRouteBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.route.vrf.route.VrfNextHopsBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.route.vrf.route.vrf.next.hops.NextHopAddressBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.unicast.VrfUnicastBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.xr.types.rev150119.CiscoIosXrString;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.ListNodesInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.ListNodesOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.NcmountService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.ShowNodeInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.ShowNodeOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.WriteRoutesInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.WriteRoutesOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.WriteRoutesOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.write.routes.input.Route;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.write.routes.input.RouteKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NcmountServiceImpl implements NcmountService {

    private DataBroker dataBroker;
    private MountPointService mountPointService;

    public static final InstanceIdentifier<Topology> NETCONF_TOPO_IID =
            InstanceIdentifier.create(NetworkTopology.class)
                    .child(Topology.class, new TopologyKey(new TopologyId(TopologyNetconf.QNAME.getLocalName())));

    LoadingCache<String, KeyedInstanceIdentifier<Node, NodeKey>> mountIds = CacheBuilder.newBuilder()
            .maximumSize(20).build(
                    new CacheLoader<>() {
                        @Override
                        public KeyedInstanceIdentifier<Node, NodeKey> load(final String key) {
                            return NETCONF_TOPO_IID.child(Node.class, new NodeKey(new NodeId(key)));
                        }
                    });

    public NcmountServiceImpl(DataBroker dataBroker, MountPointService mountPointService) {
        this.dataBroker = dataBroker;
        this.mountPointService = mountPointService;
    }


    @Override
    public ListenableFuture<RpcResult<WriteRoutesOutput>> writeRoutes(WriteRoutesInput input) {
        try {

            final Optional<MountPoint> mountPoint;
            mountPoint = mountPointService.getMountPoint(mountIds.get(input.getMountName()));

            final DataBroker dataBroker = mountPoint.get().getService(DataBroker.class).get();
            final WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();

            final CiscoIosXrString name = new CiscoIosXrString(input.getVrfId());
            final InstanceIdentifier<Vrf> routesInstanceId = InstanceIdentifier.create(RouterStatic.class)
                    .child(Vrfs.class).child(Vrf.class, new VrfKey(name));

            Map<VrfPrefixKey, VrfPrefix> routes = new HashMap<>();
            for (Map.Entry<RouteKey, Route> entry : input.getRoute().entrySet()) {
                final IpAddress prefix = new IpAddress(entry.getValue().getIpv4Prefix());
                final IpAddress nextHop = new IpAddress(entry.getValue().getIpv4NextHop());
                final long prefixLength = entry.getValue().getIpv4PrefixLength().longValue();
                VrfPrefix vrfPrefix = new VrfPrefixBuilder()
                        .setVrfRoute(new VrfRouteBuilder()
                                .setVrfNextHops(new VrfNextHopsBuilder()
                                        .setNextHopAddress(Collections.singletonList(new NextHopAddressBuilder()
                                                .setNextHopAddress(nextHop)
                                                .build()))
                                        .build())
                                .build())
                        .setPrefix(prefix)
                        .setPrefixLength(entry.getValue().getIpv4PrefixLength().toUint32())
                        .withKey(new VrfPrefixKey(prefix, prefixLength))
                        .build();
                routes.put(new VrfPrefixKey(prefix, prefixLength), vrfPrefix);
            }

            final VrfPrefixes transformedRoutes = new VrfPrefixesBuilder()
                    .setVrfPrefix(routes).build();

            final Vrf newRoutes = new VrfBuilder()
                    .setVrfName(name)
                    .withKey(new VrfKey(name))
                    .setAddressFamily(new AddressFamilyBuilder()
                            .setVrfipv4(new Vrfipv4Builder()
                                    .setVrfUnicast(new VrfUnicastBuilder()
                                            .setVrfPrefixes(transformedRoutes)
                                            .build())
                                    .build())
                            .build())
                    .build();

            writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, routesInstanceId, newRoutes);
            writeTransaction.commit().get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            e.printStackTrace();
        }

        WriteRoutesOutputBuilder outputBuilder = new WriteRoutesOutputBuilder();
        return Futures.immediateFuture(RpcResultBuilder.success(outputBuilder).build());
    }

    @Override
    public ListenableFuture<RpcResult<ShowNodeOutput>> showNode(ShowNodeInput input) {
        return null;
    }

    @Override
    public ListenableFuture<RpcResult<ListNodesOutput>> listNodes(ListNodesInput input) {
        return null;
    }
}
