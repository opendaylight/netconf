/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.test.endtoend;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.MountPointService;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.topology.spi.NetconfNodeUtils;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.RouterStatic;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.address.family.AddressFamilyBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.address.family.address.family.Vrfipv4Builder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.router._static.Vrfs;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.router._static.vrfs.Vrf;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.router._static.vrfs.VrfBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.router._static.vrfs.VrfKey;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.prefix.table.VrfPrefixesBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.prefix.table.vrf.prefixes.VrfPrefixBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.prefix.table.vrf.prefixes.VrfPrefixKey;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.route.VrfRouteBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.route.vrf.route.VrfNextHopsBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.route.vrf.route.vrf.next.hops.NextHopAddressBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.unicast.VrfUnicastBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.xr.types.rev150119.CiscoIosXrString;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.WriteRoutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.WriteRoutesInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.WriteRoutesOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmount.rev150105.WriteRoutesOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.binding.util.BindingMap;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

record WriteRoutesImpl(MountPointService mountPointService) implements WriteRoutes {
    WriteRoutesImpl {
        requireNonNull(mountPointService);
    }

    @Override
    public ListenableFuture<RpcResult<WriteRoutesOutput>> invoke(final WriteRoutesInput input) {
        final var optMountPoint = mountPointService.findMountPoint(NetconfNodeUtils.DEFAULT_TOPOLOGY_OID.toBuilder()
            .child(Node.class, new NodeKey(new NodeId(input.getMountName())))
            .build());
        if (optMountPoint.isEmpty()) {
            return RpcResultBuilder.<WriteRoutesOutput>failed()
                .withError(ErrorType.TRANSPORT, "Mount point not present")
                .buildFuture();
        }

        final var optBroker = optMountPoint.orElseThrow().getService(DataBroker.class);
        if (optBroker.isEmpty()) {
            return RpcResultBuilder.<WriteRoutesOutput>failed()
                .withError(ErrorType.TRANSPORT, "Mount point does not provide DataBroker service")
                .buildFuture();
        }

        final var vrf = new VrfBuilder()
            .withKey(new VrfKey(new CiscoIosXrString(input.getVrfId())))
            .setAddressFamily(new AddressFamilyBuilder()
                .setVrfipv4(new Vrfipv4Builder()
                    .setVrfUnicast(new VrfUnicastBuilder()
                        .setVrfPrefixes(new VrfPrefixesBuilder()
                            .setVrfPrefix(input.nonnullRoute().entrySet().stream()
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
                                .collect(BindingMap.toOrderedMap()))
                            .build())
                        .build())
                    .build())
                .build())
            .build();

        final var writeTransaction = optBroker.orElseThrow().newWriteOnlyTransaction();
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION,
            DataObjectIdentifier.builder(RouterStatic.class).child(Vrfs.class).child(Vrf.class, vrf.key()).build(),
            vrf);

        return writeTransaction.commit().transform(
            info -> RpcResultBuilder.success(new WriteRoutesOutputBuilder().build()).build(),
            MoreExecutors.directExecutor());
    }
}

