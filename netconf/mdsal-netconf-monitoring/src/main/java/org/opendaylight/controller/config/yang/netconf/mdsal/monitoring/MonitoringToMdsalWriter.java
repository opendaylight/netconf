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
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.function.Consumer;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes netconf server state changes received from NetconfMonitoringService to netconf-state datastore subtree.
 */
public final class MonitoringToMdsalWriter implements AutoCloseable, NetconfMonitoringService.CapabilitiesListener,
        NetconfMonitoringService.SessionsListener {

    private static final Logger LOG = LoggerFactory.getLogger(MonitoringToMdsalWriter.class);

    private static final InstanceIdentifier<Capabilities> CAPABILITIES_INSTANCE_IDENTIFIER =
            InstanceIdentifier.create(NetconfState.class).child(Capabilities.class);
    private static final InstanceIdentifier<Schemas> SCHEMAS_INSTANCE_IDENTIFIER =
            InstanceIdentifier.create(NetconfState.class).child(Schemas.class);
    private static final InstanceIdentifier<Sessions> SESSIONS_INSTANCE_IDENTIFIER =
            InstanceIdentifier.create(NetconfState.class).child(Sessions.class);

    private final NetconfMonitoringService serverMonitoringDependency;
    private final DataBroker dataBroker;

    public MonitoringToMdsalWriter(final NetconfMonitoringService serverMonitoringDependency,
                                   final DataBroker dataBroker) {
        this.serverMonitoringDependency = serverMonitoringDependency;
        this.dataBroker = dataBroker;
    }

    /**
     * Invoked using blueprint.
     */
    @Override
    public void close() {
        runTransaction((tx) -> tx.delete(LogicalDatastoreType.OPERATIONAL,
                InstanceIdentifier.create(NetconfState.class)));
    }

    @Override
    public void onSessionStarted(final Session session) {
        final InstanceIdentifier<Session> sessionPath =
                SESSIONS_INSTANCE_IDENTIFIER.child(Session.class, session.key());
        runTransaction((tx) -> tx.put(LogicalDatastoreType.OPERATIONAL, sessionPath, session));
    }

    @Override
    public void onSessionEnded(final Session session) {
        final InstanceIdentifier<Session> sessionPath =
                SESSIONS_INSTANCE_IDENTIFIER.child(Session.class, session.key());
        runTransaction((tx) -> tx.delete(LogicalDatastoreType.OPERATIONAL, sessionPath));
    }

    @Override
    public void onSessionsUpdated(final Collection<Session> sessions) {
        runTransaction((tx) -> updateSessions(tx, sessions));
    }

    @Override
    public void onCapabilitiesChanged(final Capabilities capabilities) {
        runTransaction((tx) -> tx.put(LogicalDatastoreType.OPERATIONAL, CAPABILITIES_INSTANCE_IDENTIFIER,
                capabilities));
    }

    @Override
    public void onSchemasChanged(final Schemas schemas) {
        runTransaction((tx) -> tx.put(LogicalDatastoreType.OPERATIONAL, SCHEMAS_INSTANCE_IDENTIFIER, schemas));
    }

    /**
     * Invoked using blueprint.
     */
    public void start() {
        serverMonitoringDependency.registerCapabilitiesListener(this);
        serverMonitoringDependency.registerSessionsListener(this);
    }

    private void runTransaction(final Consumer<WriteTransaction> txUser) {
        Preconditions.checkState(dataBroker != null);
        final WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        txUser.accept(tx);
        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Netconf state updated successfully");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("Unable to update netconf state", throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    private static void updateSessions(final WriteTransaction tx, final Collection<Session> sessions) {
        for (Session session : sessions) {
            final InstanceIdentifier<Session> sessionPath =
                    SESSIONS_INSTANCE_IDENTIFIER.child(Session.class, session.key());
            tx.put(LogicalDatastoreType.OPERATIONAL, sessionPath, session);
        }
    }
}
