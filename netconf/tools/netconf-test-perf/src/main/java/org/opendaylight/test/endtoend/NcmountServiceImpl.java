/*
 * Copyright (c) 2021 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.test.endtoend;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Map;
import java.util.Optional;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.MountPoint;
import org.opendaylight.mdsal.binding.api.MountPointService;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.RouterStatic;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.address.family.AddressFamilyBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.address.family.address.family.Vrfipv4Builder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.router._static.Vrfs;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.router._static.vrfs.Vrf;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.router._static.vrfs.VrfBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.router._static.vrfs.VrfKey;
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
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.util.BindingMap;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

public class NcmountServiceImpl implements NcmountService {
    private static final InstanceIdentifier<Topology> NETCONF_TOPO_IID =
            InstanceIdentifier.create(NetworkTopology.class)
                    .child(Topology.class, new TopologyKey(new TopologyId(TopologyNetconf.QNAME.getLocalName())));

    private final MountPointService mountPointService;

    public NcmountServiceImpl(final MountPointService mountPointService) {
        this.mountPointService = requireNonNull(mountPointService);
    }

    @Override
    public ListenableFuture<RpcResult<WriteRoutesOutput>> writeRoutes(final WriteRoutesInput input) {
        final Optional<MountPoint> optMountPoint = mountPointService.getMountPoint(
            NETCONF_TOPO_IID.child(Node.class, new NodeKey(new NodeId(input.getMountName()))));
        if (optMountPoint.isEmpty()) {
            return RpcResultBuilder.<WriteRoutesOutput>failed()
                .withError(ErrorType.TRANSPORT, "Mount point not present")
                .buildFuture();
        }

        final Optional<DataBroker> optBroker = optMountPoint.orElseThrow().getService(DataBroker.class);
        if (optBroker.isEmpty()) {
            return RpcResultBuilder.<WriteRoutesOutput>failed()
                .withError(ErrorType.TRANSPORT, "Mount point does not provide DataBroker service")
                .buildFuture();
        }

        final Map<VrfPrefixKey, VrfPrefix> routes = input.nonnullRoute().entrySet().stream()
            .map(entry -> new VrfPrefixBuilder()
                .withKey(new VrfPrefixKey(new IpAddress(entry.getValue().getIpv4Prefix()),
                    entry.getValue().getIpv4PrefixLength().toUint32()))
                .setVrfRoute(new VrfRouteBuilder()
                    .setVrfNextHops(new VrfNextHopsBuilder()
                        .setNextHopAddress(BindingMap.of(new NextHopAddressBuilder()
                            .setNextHopAddress(new IpAddress(entry.getValue().getIpv4NextHop()))
                            .build()))
                        .build())
                    .build())
                .build())
            .collect(BindingMap.toOrderedMap());

        final Vrf vrf = new VrfBuilder()
            .withKey(new VrfKey(new CiscoIosXrString(input.getVrfId())))
            .setAddressFamily(new AddressFamilyBuilder()
                .setVrfipv4(new Vrfipv4Builder()
                    .setVrfUnicast(new VrfUnicastBuilder()
                        .setVrfPrefixes(new VrfPrefixesBuilder()
                            // FIXME: inline routes once it is unambiguous
                            .setVrfPrefix(routes)
                            .build())
                        .build())
                    .build())
                .build())
            .build();

        final WriteTransaction writeTransaction = optBroker.orElseThrow().newWriteOnlyTransaction();
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION,
            InstanceIdentifier.create(RouterStatic.class).child(Vrfs.class).child(Vrf.class, vrf.key()), vrf);

        return writeTransaction.commit().transform(
            info -> RpcResultBuilder.success(new WriteRoutesOutputBuilder()).build(), MoreExecutors.directExecutor());
     }

    @Override
    public ListenableFuture<RpcResult<ShowNodeOutput>> showNode(final ShowNodeInput input) {
        return RpcResultBuilder.<ShowNodeOutput>failed()
            .withError(ErrorType.APPLICATION, "Not implemented")
            .buildFuture();
    }

    @Override
    public ListenableFuture<RpcResult<ListNodesOutput>> listNodes(final ListNodesInput input) {
        return RpcResultBuilder.<ListNodesOutput>failed()
            .withError(ErrorType.APPLICATION, "Not implemented")
            .buildFuture();
    }
}
