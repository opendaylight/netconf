/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.monitoring;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.$YangModuleInfoImpl.qnameOf;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.checkerframework.checker.lock.qual.Holding;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeTransaction;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;
import org.opendaylight.restconf.api.query.AbstractReplayParam;
import org.opendaylight.restconf.api.query.ChangedLeafNodesOnlyParam;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.api.query.FilterParam;
import org.opendaylight.restconf.api.query.LeafNodesOnlyParam;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.api.query.SkipNotificationDataParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.RestconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.Capabilities;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple component which maintains {@link Capabilities} in the operational datastore.
 */
@Singleton
@Component(service = { })
public final class CapabilitiesWriter
        implements AutoCloseable, EffectiveModelContextListener, DOMTransactionChainListener {
    private static final Logger LOG = LoggerFactory.getLogger(CapabilitiesWriter.class);

    @VisibleForTesting
    static final @NonNull NodeIdentifier CAPABILITY = NodeIdentifier.create(qnameOf("capability"));

    private static final YangInstanceIdentifier PATH = YangInstanceIdentifier.of(
        NodeIdentifier.create(RestconfState.QNAME), NodeIdentifier.create(Capabilities.QNAME), CAPABILITY);

    private final DOMDataBroker dataBroker;

    private DOMTransactionChain txChain;
    private Registration reg;

    private boolean written;

    @Inject
    @Activate
    public CapabilitiesWriter(@Reference final DOMDataBroker dataBroker,
            @Reference final DOMSchemaService schemaService) {
        this.dataBroker = requireNonNull(dataBroker);
        reg = schemaService.registerSchemaContextListener(this);
    }

    @PreDestroy
    @Deactivate
    @Override
    public synchronized void close() {
        if (reg == null) {
            return;
        }
        reg.close();
        reg = null;
        deleteRestconfState();
        if (txChain != null) {
            txChain.close();
        }
    }

    @Override
    public synchronized void onTransactionChainFailed(final DOMTransactionChain chain,
            final DOMDataTreeTransaction transaction, final Throwable cause) {
        LOG.warn("Transaction chain failed, updates may not have been propagated", cause);
        txChain = null;
    }

    @Override
    public synchronized void onTransactionChainSuccessful(final DOMTransactionChain chain) {
        LOG.debug("Transaction chain closed successfully");
        txChain = null;
    }

    @Override
    public synchronized void onModelContextUpdated(final EffectiveModelContext newModelContext) {
        if (reg != null) {
            LOG.debug("Ignoring model context update");
            return;
        }

        if (newModelContext.findModuleStatement(RestconfState.QNAME.getModule()).isPresent()) {
            writeRestconfState();
        } else {
            deleteRestconfState();
        }
    }

    @Holding("this")
    private void deleteRestconfState() {
        if (!written) {
            LOG.debug("No state recorded as written, not attempting removal");
            return;
        }

        LOG.debug("Removing ietf-restconf-monitoring state");
        if (txChain == null) {
            txChain = dataBroker.createMergingTransactionChain(this);
        }

        final var tx = txChain.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, PATH);
        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                markUnwritten();
            }

            @Override
            public void onFailure(final Throwable cause) {
                // Ignored, will be reported on the transaction chain
            }
        }, MoreExecutors.directExecutor());
    }

    @Holding("this")
    private void writeRestconfState() {
        if (written) {
            LOG.debug("State recorded as written, not updating it");
            return;
        }

        LOG.debug("Updating state of ietf-restconf-monitoring");
        if (txChain == null) {
            txChain = dataBroker.createMergingTransactionChain(this);
        }

        final var tx = txChain.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, PATH, mapCapabilites());
        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                markWritten();
            }

            @Override
            public void onFailure(final Throwable cause) {
                // Ignored, will be reported on the transaction chain
            }
        }, MoreExecutors.directExecutor());
    }

    private synchronized void markWritten() {
        LOG.debug("State of ietf-restconf-monitoring updated");
        written = true;
    }

    private synchronized void markUnwritten() {
        LOG.debug("State of ietf-restconf-monitoring removed");
        written = false;
    }

    /**
     * Create a {@code restconf-state} container.
     *
     * @return A container holding capabilities
     */
    @VisibleForTesting
    static @NonNull LeafSetNode<String> mapCapabilites() {
        return Builders.<String>orderedLeafSetBuilder()
            .withNodeIdentifier(CAPABILITY)
            .withChildValue(DepthParam.capabilityUri().toString())
            .withChildValue(FieldsParam.capabilityUri().toString())
            .withChildValue(FilterParam.capabilityUri().toString())
            .withChildValue(AbstractReplayParam.capabilityUri().toString())
            .withChildValue(WithDefaultsParam.capabilityUri().toString())
            .withChildValue(PrettyPrintParam.capabilityUri().toString())
            .withChildValue(LeafNodesOnlyParam.capabilityUri().toString())
            .withChildValue(ChangedLeafNodesOnlyParam.capabilityUri().toString())
            .withChildValue(SkipNotificationDataParam.capabilityUri().toString())
            .build();
    }
}
