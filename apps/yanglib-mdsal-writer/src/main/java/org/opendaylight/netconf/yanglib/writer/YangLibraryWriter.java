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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.checkerframework.checker.lock.qual.GuardedBy;
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
        @AttributeDefinition(description = "Enables legacy content to be written")
        boolean write$_$legacy() default false;
    }

    private static final Logger LOG = LoggerFactory.getLogger(YangLibraryWriter.class);
    private static final InstanceIdentifier<YangLibrary> YANG_LIBRARY_INSTANCE_IDENTIFIER =
        InstanceIdentifier.create(YangLibrary.class);
    private static final InstanceIdentifier<ModulesState> MODULES_STATE_INSTANCE_IDENTIFIER =
        InstanceIdentifier.create(ModulesState.class);

    private final AtomicLong idCounter = new AtomicLong(0L);
    private final DataBroker dataBroker;
    private final boolean writeLegacy;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    volatile YangLibrarySchemaSourceUrlProvider schemaSourceUrlProvider;

    @GuardedBy("this")
    private Registration reg;

    @Inject
    @Activate
    public YangLibraryWriter(final @Reference DOMSchemaService schemaService,
            final @Reference DataBroker dataBroker, final Configuration configuration) {
        this.dataBroker = requireNonNull(dataBroker);
        writeLegacy = configuration.write$_$legacy();
        reg = schemaService.registerSchemaContextListener(this::onModelContextUpdated);
    }

    @Deactivate
    @PreDestroy
    @Override
    public synchronized void close() throws InterruptedException, ExecutionException {
        if (reg == null) {
            // Already shut down
            return;
        }
        reg.close();
        reg = null;

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
                LOG.debug("YANG library cleared successfully");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("Unable to clear YANG library", throwable);
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
        if (reg == null) {
            // Already shut down, do not do anything
            return;
        }
        final var nextId = String.valueOf(idCounter.incrementAndGet());
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, YANG_LIBRARY_INSTANCE_IDENTIFIER,
            YangLibraryContentBuilderUtil.buildYangLibrary(context, nextId, schemaSourceUrlProvider));
        if (writeLegacy) {
            tx.put(LogicalDatastoreType.OPERATIONAL, MODULES_STATE_INSTANCE_IDENTIFIER,
                YangLibraryContentBuilderUtil.buildModuleState(context, nextId, schemaSourceUrlProvider));
        }

        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Yang library updated successfully");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("Failed to update yang library", throwable);
            }
        }, MoreExecutors.directExecutor());
    }
}
