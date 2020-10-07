/*
 * Copyright (c) 2020 OpendayLight and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.api.MountPointManagerService;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.KeepaliveSalFacade;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalFacade;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseSchema;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.NetconfMessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.util.MountPointHandler;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfSalFacadeType;
import org.opendaylight.netconf.sal.connect.util.Capability;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfMountPointManager
        implements RemoteDeviceHandler<NetconfSessionPreferences>, MountPointManagerService<NetconfSessionPreferences> {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfMountPointManager.class);
    private final Map<String, MountPointHandler> mountPointHandlerMap = new ConcurrentHashMap<>();
    private final Map<MountPointContext, NetconfMessageTransformer> mountPointMessageTransformerMap
            = new ConcurrentHashMap<>();
    private final Map<Capability, MountPointContext> capabilityMountPointContextMap = new ConcurrentHashMap<>();

    @Override
    public void updateNetconfMountPointHandler(String nodeId, MountPointContext mountPointContext,
            NetconfSessionPreferences netconfSessionPreferences, BaseSchema baseSchema) {
        MountPointContext
                cachedMountPointContext =
                compareAndGetMountPointContext(mountPointContext, netconfSessionPreferences);
        mountPointHandlerMap.compute(nodeId, (key, mountPointHandler) -> {
            mountPointHandler.setCachedMountPointContext(cachedMountPointContext);
            return mountPointHandler;
        });
        mountPointMessageTransformerMap.computeIfAbsent(cachedMountPointContext,key ->
            new NetconfMessageTransformer(key, true, baseSchema));
    }

    @Override
    public  NetconfMessageTransformer getNetconfMessageTransformer(MountPointContext mountPointContext) {
        return mountPointMessageTransformerMap.get(mountPointContext);
    }

    @Override
    public  RemoteDeviceHandler getInstance(final RemoteDeviceId id,
            final DOMMountPointService mountPointService, final DataBroker dataBroker, final String topologyId,
            final NetconfSalFacadeType netconfSalFacadeType, final ScheduledExecutorService executor,
            final Long keepaliveDelaySeconds, final Long defaultRequestTimeoutMillis) {
        switch (netconfSalFacadeType) {
            case KEEPALIVESALFACADE: {
                KeepaliveSalFacade
                        keepaliveSalFacade =
                        new KeepaliveSalFacade(id,
                                new NetconfDeviceSalFacade(id, mountPointService, dataBroker, topologyId), executor,
                                keepaliveDelaySeconds, defaultRequestTimeoutMillis);
                MountPointHandler mountPointHandler = new MountPointHandler();
                mountPointHandler.setRemoteDeviceHandler(keepaliveSalFacade);
                mountPointHandlerMap.put(id.getName(), mountPointHandler);
                return this;
            }
            case NETCONFDEVICESALFACADE: {
                MountPointHandler mountPointHandler = new MountPointHandler();
                mountPointHandler.setRemoteDeviceHandler(
                        new NetconfDeviceSalFacade(id, mountPointService, dataBroker, topologyId));
                mountPointHandlerMap.put(id.getName(), mountPointHandler);
                return this;
            }
            default: {
                throw new IllegalStateException("Unexpected value: " + netconfSalFacadeType.name());
            }
        }

    }

    @Override
    public  MountPointContext getMountPointContextByNodeId(String nodeId) {
        return mountPointHandlerMap.get(nodeId).getCachedMountPointContext();
    }

    private  MountPointContext compareAndGetMountPointContext(MountPointContext mountPointContext,
            NetconfSessionPreferences netconfSessionPreferences) {
        Capability capability = new Capability(netconfSessionPreferences.getNonModuleCaps(),
                netconfSessionPreferences.getModuleBasedCaps());
        MountPointContext cachedMountPointContext = capabilityMountPointContextMap.get(capability);
        if (cachedMountPointContext == null) {
            capabilityMountPointContextMap.put(capability, mountPointContext);
            return mountPointContext;
        } else {
            return cachedMountPointContext;
        }
    }

    @Override
    public void onDeviceDisconnected() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onDeviceDisconnected(String nodeId) {
        MountPointHandler mountPointHandler = mountPointHandlerMap.get(nodeId);

        mountPointHandler.getRemoteDeviceHandler().onDeviceDisconnected();
        Long count = mountPointHandlerMap.values()
                        .stream()
                        .filter(value -> value.getCachedMountPointContext()
                                == mountPointHandler.getCachedMountPointContext())
                        .count();
        if (count == 1) {
            mountPointMessageTransformerMap.remove(mountPointHandler.getCachedMountPointContext());
            capabilityMountPointContextMap.entrySet()
                    .removeIf(capabilityMountPointContextEntry -> capabilityMountPointContextEntry.getValue()
                            == (mountPointHandler.getCachedMountPointContext()));
        }
        mountPointHandlerMap.remove(nodeId);
    }

    @Override
    public void onDeviceFailed(Throwable throwable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onDeviceFailed(String nodeId, Throwable throwable) {
        mountPointHandlerMap.get(nodeId).getRemoteDeviceHandler().onDeviceFailed(throwable);
    }

    @Override
    public void onDeviceConnected(final String nodeId, final NetconfSessionPreferences netconfSessionPreferences,
            final DOMRpcService deviceRpc, final DOMActionService deviceAction) {
        MountPointHandler mountPointHandler = mountPointHandlerMap.get(nodeId);
        mountPointHandler.getRemoteDeviceHandler()
                .onDeviceConnected(mountPointHandler.getCachedMountPointContext(), netconfSessionPreferences,
                        deviceRpc, deviceAction);
    }

    @Override
    public void onDeviceConnected(String nodeId, final MountPointContext remoteSchemaContext,
            final NetconfSessionPreferences netconfSessionPreferences, final DOMRpcService deviceRpc) {
        MountPointHandler mountPointHandler = mountPointHandlerMap.get(nodeId);
        mountPointHandler.getRemoteDeviceHandler()
                .onDeviceConnected(remoteSchemaContext, netconfSessionPreferences, deviceRpc, null);
    }

    @Override
    public void onDeviceReconnected(String nodeId, final NetconfSessionPreferences netconfSessionPreferences,
            final NetconfNode node) {
        mountPointHandlerMap.get(nodeId).getRemoteDeviceHandler()
                .onDeviceReconnected(netconfSessionPreferences, node);
    }


    @Override
    public void onNotification(String id, final DOMNotification domNotification) {
        mountPointHandlerMap.get(id).getRemoteDeviceHandler().onNotification(domNotification);
    }

    @Override
    public void onNotification(final DOMNotification domNotification) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close(String nodeId) {
        mountPointHandlerMap.get(nodeId).getRemoteDeviceHandler().close();
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException();
    }

}
