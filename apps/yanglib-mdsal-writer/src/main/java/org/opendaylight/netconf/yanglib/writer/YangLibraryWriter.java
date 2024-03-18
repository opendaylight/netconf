/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.yanglib.writer;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibrary;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for updates on global schema context, transforms context to ietf-yang-library/yang-library and writes this
 * state to operational data store.
 */
@Singleton
@Component(service = { }, configurationPid = "org.opendaylight.netconf.yanglib")
@Designate(ocd = YangLibraryWriter.Configuration.class)
public final class YangLibraryWriter implements AutoCloseable {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition(description = "Maintain RFC7895-compatible modules-state container. Defaults to true.")
        boolean write$_$legacy() default true;
    }

    private static final Logger LOG = LoggerFactory.getLogger(YangLibraryWriter.class);
    private static final InstanceIdentifier<YangLibrary> YANG_LIBRARY_INSTANCE_IDENTIFIER =
        InstanceIdentifier.create(YangLibrary.class);
    private static final InstanceIdentifier<ModulesState> MODULES_STATE_INSTANCE_IDENTIFIER =
        InstanceIdentifier.create(ModulesState.class);

    private final AtomicLong idCounter = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final @NonNull YangLibrarySchemaSourceUrlProvider urlProvider;
    private final DataBroker dataBroker;
    private final boolean writeLegacy;
    private final Registration reg;

    public YangLibraryWriter(final DOMSchemaService schemaService, final DataBroker dataBroker,
            final boolean writeLegacy, final @Nullable YangLibrarySchemaSourceUrlProvider urlProvider) {
        this.dataBroker = requireNonNull(dataBroker);
        this.urlProvider = orEmptyProvider(urlProvider);
        this.writeLegacy = writeLegacy;
        reg = schemaService.registerSchemaContextListener(this::onModelContextUpdated);
        LOG.info("ietf-yang-library writer started with modules-state {}", writeLegacy ? "enabled" : "disabled");
    }

    public YangLibraryWriter(final DOMSchemaService schemaService, final DataBroker dataBroker,
            final boolean writeLegacy) {
        this(schemaService, dataBroker, writeLegacy, null);
    }

    @Inject
    public YangLibraryWriter(final DOMSchemaService schemaService, final DataBroker dataBroker,
            final Optional<YangLibrarySchemaSourceUrlProvider> urlProvider) {
        this(schemaService, dataBroker, true, urlProvider.orElse(null));
    }

    @Activate
    public YangLibraryWriter(final @Reference DOMSchemaService schemaService, @Reference final DataBroker dataBroker,
            @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
                final @Nullable YangLibrarySchemaSourceUrlProvider urlProvider,
            final Configuration configuration) {
        this(schemaService, dataBroker, configuration.write$_$legacy(), urlProvider);
    }

    private static @NonNull YangLibrarySchemaSourceUrlProvider orEmptyProvider(
            final @Nullable YangLibrarySchemaSourceUrlProvider urlProvider) {
        return urlProvider != null ? urlProvider : emptyProvider();
    }

    private static @NonNull YangLibrarySchemaSourceUrlProvider emptyProvider() {
        return (moduleSetName, moduleName, revision) -> Set.of();
    }

    @Deactivate
    @PreDestroy
    @Override
    public synchronized void close() throws InterruptedException, ExecutionException {
        if (!closed.compareAndSet(false, true)) {
            // Already shut down
            return;
        }
        reg.close();

        // FIXME: we should be using a transaction chain for this, but, really, this should be a dynamically-populated
        //        shard (i.e. no storage whatsoever)!
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, YANG_LIBRARY_INSTANCE_IDENTIFIER);
        if (writeLegacy) {
            tx.delete(LogicalDatastoreType.OPERATIONAL, MODULES_STATE_INSTANCE_IDENTIFIER);
        }

        final var future = tx.commit();
        future.addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo info) {
                LOG.info("ietf-yang-library writer stopped");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("ietf-yang-library writer stopped uncleanly", throwable);
            }
        }, MoreExecutors.directExecutor());

        // We need to synchronize here, otherwise we'd end up trampling over ourselves
        future.get();
    }

    @VisibleForTesting void onModelContextUpdated(final EffectiveModelContext context) {
        if (context.findModule(YangLibrary.QNAME.getModule()).isPresent()) {
            updateYangLibrary(context);
        } else {
            LOG.warn("ietf-yang-library not present in context, skipping update");
        }
    }

    private synchronized void updateYangLibrary(final EffectiveModelContext context) {
        if (closed.get()) {
            // Already shut down, do not do anything
            LOG.debug("ietf-yang-library writer closed, skipping update");
            return;
        }

        final var nextId = String.valueOf(idCounter.incrementAndGet());
        LOG.debug("ietf-yang-library writer starting update to {}", nextId);
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, YANG_LIBRARY_INSTANCE_IDENTIFIER,
            YangLibraryContentBuilderUtil.buildYangLibrary(context, nextId, urlProvider));
        if (writeLegacy) {
            tx.put(LogicalDatastoreType.OPERATIONAL, MODULES_STATE_INSTANCE_IDENTIFIER,
                YangLibraryContentBuilderUtil.buildModuleState(context, nextId, urlProvider));
        }

        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("ietf-yang-library updated successfully");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("Failed to update ietf-yang-library", throwable);
            }
        }, MoreExecutors.directExecutor());
    }
}
