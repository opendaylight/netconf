/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.netconf.mdsal.monitoring;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MonitoringToMdsalWriter implements AutoCloseable, NetconfMonitoringService.MonitoringListener, BindingAwareProvider {

    private static final Logger LOG = LoggerFactory.getLogger(MonitoringToMdsalWriter.class);

    private static final InstanceIdentifier<Capabilities> CAPABILITIES_INSTANCE_IDENTIFIER =
            InstanceIdentifier.create(NetconfState.class).child(Capabilities.class);
    private static final InstanceIdentifier<Schemas> SCHEMAS_INSTANCE_IDENTIFIER =
            InstanceIdentifier.create(NetconfState.class).child(Schemas.class);
    private static final InstanceIdentifier<Sessions> SESSIONS_INSTANCE_IDENTIFIER =
            InstanceIdentifier.create(NetconfState.class).child(Sessions.class);

    private final NetconfMonitoringService serverMonitoringDependency;
    private DataBroker dataBroker;

    public MonitoringToMdsalWriter(final NetconfMonitoringService serverMonitoringDependency) {
        this.serverMonitoringDependency = serverMonitoringDependency;
    }

    @Override
    public void close() {
        deleteFromDatastore(InstanceIdentifier.create(NetconfState.class));
    }

    @Override
    public void onSessionStarted(Session session) {
        final InstanceIdentifier<Session> sessionPath =
                SESSIONS_INSTANCE_IDENTIFIER.child(Session.class, session.getKey());
        putToDatastore(sessionPath, session);
    }

    @Override
    public void onSessionEnded(Session session) {
        final InstanceIdentifier<Session> sessionPath =
                SESSIONS_INSTANCE_IDENTIFIER.child(Session.class, session.getKey());
        deleteFromDatastore(sessionPath);
    }

    @Override
    public void onCapabilitiesChanged(Capabilities capabilities) {
        putToDatastore(CAPABILITIES_INSTANCE_IDENTIFIER, capabilities);
    }

    @Override
    public void onSchemasChanged(Schemas schemas) {
        putToDatastore(SCHEMAS_INSTANCE_IDENTIFIER, schemas);
    }

    @Override
    public void onSessionInitiated(final BindingAwareBroker.ProviderContext providerContext) {
        dataBroker = providerContext.getSALService(DataBroker.class);
        serverMonitoringDependency.registerListener(this);
    }

    private <T extends DataObject> void putToDatastore(InstanceIdentifier<T> path, T value) {
        Preconditions.checkState(dataBroker != null);
        final WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, path, value);
        Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                LOG.debug("Netconf state updated successfully");
            }

            @Override
            public void onFailure(Throwable t) {
                LOG.warn("Unable to update netconf state", t);
            }
        });
    }

    private <T extends DataObject> void deleteFromDatastore(InstanceIdentifier<T> path) {
        Preconditions.checkState(dataBroker != null);
        final WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, path);
        Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                LOG.debug("Netconf state updated successfully");
            }

            @Override
            public void onFailure(Throwable t) {
                LOG.warn("Unable to update netconf state", t);
            }
        });
    }
}
