/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.yanglib.writer;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.TransactionChain;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibrary;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for updates on global schema context, transforms context to ietf-yang-library/yang-library and writes this
 * state to operational data store.
 */
final class YangLibraryWriter implements FutureCallback<Empty> {
    private static final Logger LOG = LoggerFactory.getLogger(YangLibraryWriter.class);
    private static final InstanceIdentifier<YangLibrary> YANG_LIBRARY_INSTANCE_IDENTIFIER =
        InstanceIdentifier.create(YangLibrary.class);
    @Deprecated
    private static final InstanceIdentifier<ModulesState> MODULES_STATE_INSTANCE_IDENTIFIER =
        InstanceIdentifier.create(ModulesState.class);

    private final AtomicLong idCounter = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final DOMSchemaService schemaService;
    private final DataBroker dataBroker;
    private final boolean writeLegacy;
    private final Registration reg;

    // FIXME: this really should be a dynamically-populated shard (i.e. no write operations whatsoever)!
    private @NonNull YangLibrarySchemaSourceUrlProvider urlProvider;
    private TransactionChain currentChain;

    YangLibraryWriter(final DOMSchemaService schemaService, final DataBroker dataBroker,
            final boolean writeLegacy, final YangLibrarySchemaSourceUrlProvider urlProvider) {
        this.schemaService = requireNonNull(schemaService);
        this.dataBroker = requireNonNull(dataBroker);
        this.urlProvider = requireNonNull(urlProvider);
        this.writeLegacy = writeLegacy;
        reg = schemaService.registerSchemaContextListener(this::onModelContextUpdated);
        LOG.info("ietf-yang-library writer started with modules-state {}", writeLegacy ? "enabled" : "disabled");
    }

    synchronized ListenableFuture<Empty> shutdown() {
        if (!closed.compareAndSet(false, true)) {
            // Already shut down
            return null;
        }
        reg.close();

        final ListenableFuture<Empty> future;
        try (var chain = acquireChain()) {
            future = chain.future();
            final var tx = chain.newWriteOnlyTransaction();
            tx.delete(LogicalDatastoreType.OPERATIONAL, YANG_LIBRARY_INSTANCE_IDENTIFIER);
            tx.delete(LogicalDatastoreType.OPERATIONAL, MODULES_STATE_INSTANCE_IDENTIFIER);

            tx.commit().addCallback(new FutureCallback<CommitInfo>() {
                @Override
                public void onSuccess(final CommitInfo info) {
                    LOG.debug("ietf-yang-library cleaned successfully");
                }

                @Override
                public void onFailure(final Throwable throwable) {
                    // Handled via transaction chain listener
                }
            }, MoreExecutors.directExecutor());
        }
        currentChain = null;

        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(final Empty value) {
                LOG.info("ietf-yang-library writer stopped");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("ietf-yang-library writer stopped uncleanly", throwable);
            }
        }, MoreExecutors.directExecutor());

        return future;
    }

    synchronized void setUrlProvider(final @NonNull YangLibrarySchemaSourceUrlProvider urlProvider) {
        LOG.debug("Triggering update with {}", urlProvider);
        this.urlProvider = requireNonNull(urlProvider);
        updateModelContext(schemaService.getGlobalContext());
    }

    private synchronized void onModelContextUpdated(final EffectiveModelContext context) {
        updateModelContext(context);
    }

    private void updateModelContext(final EffectiveModelContext context) {
        if (context.findModule(YangLibrary.QNAME.getModule()).isPresent()) {
            updateYangLibrary(context);
        } else {
            LOG.warn("ietf-yang-library not present in context, skipping update");
        }
    }

    private void updateYangLibrary(final EffectiveModelContext context) {
        if (closed.get()) {
            // Already shut down, do not do anything
            LOG.debug("ietf-yang-library writer closed, skipping update");
            return;
        }

        final var nextId = String.valueOf(idCounter.incrementAndGet());
        LOG.debug("ietf-yang-library writer starting update to {}", nextId);
        final var tx = acquireChain().newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, YANG_LIBRARY_INSTANCE_IDENTIFIER,
            YangLibraryContentBuilderUtil.buildYangLibrary(context, nextId, urlProvider));
        if (writeLegacy) {
            tx.put(LogicalDatastoreType.OPERATIONAL, MODULES_STATE_INSTANCE_IDENTIFIER,
                YangLibraryContentBuilderUtil.buildModuleState(context, nextId, urlProvider));
        } else {
            tx.delete(LogicalDatastoreType.OPERATIONAL, MODULES_STATE_INSTANCE_IDENTIFIER);
        }

        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("ietf-yang-library updated successfully");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                // Handled via transaction chain listener
            }
        }, MoreExecutors.directExecutor());
    }

    private TransactionChain acquireChain() {
        var local = currentChain;
        if (local == null) {
            currentChain = local = dataBroker.createMergingTransactionChain();
            LOG.debug("Allocated new transaction chain");
            local.addCallback(this);
        }
        return local;
    }

    @Override
    public synchronized void onSuccess(final Empty result) {
        LOG.debug("ietf-yang-library writer transaction chain succeeded");
        currentChain = null;
    }

    @Override
    public synchronized void onFailure(final Throwable cause) {
        LOG.info("ietf-yang-library writer transaction chain failed", cause);
        currentChain = null;
    }
}
