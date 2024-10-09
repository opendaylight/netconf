/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound.monitoring;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.function.Consumer;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService.CapabilitiesListener;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService.SessionsListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.SessionKey;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.binding.DataObjectIdentifier.WithKey;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes netconf server state changes received from NetconfMonitoringService to netconf-state datastore subtree.
 */
@Component(service = { })
public final class MonitoringToMdsalWriter implements AutoCloseable, CapabilitiesListener, SessionsListener {
    private static final Logger LOG = LoggerFactory.getLogger(MonitoringToMdsalWriter.class);

    private static final @NonNull DataObjectIdentifier<Capabilities> CAPABILITIES_OBJID =
        DataObjectIdentifier.builder(NetconfState.class).child(Capabilities.class).build();
    private static final @NonNull DataObjectIdentifier<Schemas> SCHEMAS_OBJID =
        DataObjectIdentifier.builder(NetconfState.class).child(Schemas.class).build();
    private static final @NonNull DataObjectIdentifier<Sessions> SESSIONS_OBJID =
        DataObjectIdentifier.builder(NetconfState.class).child(Sessions.class).build();

    private final DataBroker dataBroker;
    private final Registration capabilitiesReg;
    private final Registration sessionsReg;

    @Activate
    public MonitoringToMdsalWriter(@Reference final DataBroker dataBroker,
            @Reference(target = "(type=netconf-server-monitoring)")
            final NetconfMonitoringService serverMonitoringDependency) {
        this.dataBroker = requireNonNull(dataBroker);

        capabilitiesReg = serverMonitoringDependency.registerCapabilitiesListener(this);
        sessionsReg = serverMonitoringDependency.registerSessionsListener(this);
    }

    @Deactivate
    @Override
    public void close() {
        sessionsReg.close();
        capabilitiesReg.close();
        runTransaction(tx -> tx.delete(LogicalDatastoreType.OPERATIONAL,
            DataObjectIdentifier.builder(NetconfState.class).build()));
    }

    @Override
    public void onSessionStarted(final Session session) {
        runTransaction(tx -> tx.put(LogicalDatastoreType.OPERATIONAL, session(session.key()), session));
    }

    @Override
    public void onSessionEnded(final Session session) {
        runTransaction(tx -> tx.delete(LogicalDatastoreType.OPERATIONAL, session(session.key())));
    }

    @Override
    public void onSessionsUpdated(final Collection<Session> sessions) {
        runTransaction(tx -> updateSessions(tx, sessions));
    }

    @Override
    public void onCapabilitiesChanged(final Capabilities capabilities) {
        runTransaction(tx -> tx.put(LogicalDatastoreType.OPERATIONAL, CAPABILITIES_OBJID, capabilities));
    }

    @Override
    public void onSchemasChanged(final Schemas schemas) {
        runTransaction(tx -> tx.put(LogicalDatastoreType.OPERATIONAL, SCHEMAS_OBJID, schemas));
    }

    private void runTransaction(final Consumer<WriteTransaction> txUser) {
        final var tx = dataBroker.newWriteOnlyTransaction();
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
        for (var session : sessions) {
            tx.put(LogicalDatastoreType.OPERATIONAL, session(session.key()), session);
        }
    }

    private static @NonNull WithKey<Session, SessionKey> session(final @NonNull SessionKey key) {
        return SESSIONS_OBJID.toBuilder().child(Session.class, key).build();
    }
}
