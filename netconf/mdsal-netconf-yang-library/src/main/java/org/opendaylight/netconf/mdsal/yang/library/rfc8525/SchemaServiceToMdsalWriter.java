/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.yang.library.rfc8525;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.datastores.rev180214.Operational;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibrary;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.set.parameters.ImportOnlyModuleRevisionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.set.parameters.module.Submodule;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.set.parameters.module.SubmoduleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.set.parameters.module.SubmoduleKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.Datastore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.DatastoreBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.ModuleSet;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.ModuleSetBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.Schema;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.SchemaBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for updates on global schema context, transforms context to ietf-yang-library/yang-library and
 * writes this state to operational data store.
 */
public class SchemaServiceToMdsalWriter implements EffectiveModelContextListener, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SchemaServiceToMdsalWriter.class);
    private static final InstanceIdentifier<YangLibrary> YANG_LIBRARY_INSTANCE_IDENTIFIER =
            InstanceIdentifier.create(YangLibrary.class);

    private final DOMSchemaService schemaService;
    private final AtomicInteger moduleSetId;
    private final DataBroker dataBroker;

    public SchemaServiceToMdsalWriter(final DOMSchemaService schemaService,
                                      final DataBroker dataBroker) {
        this.schemaService = schemaService;
        this.dataBroker = dataBroker;
        this.moduleSetId = new AtomicInteger(0);
    }

    @Override
    public void close() {
        final WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, YANG_LIBRARY_INSTANCE_IDENTIFIER);

        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo info) {
                LOG.debug("Yang library cleared successfully");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("Unable to clear yang library", throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     * Invoked by blueprint.
     */
    public void start() {
        schemaService.registerSchemaContextListener(this);
    }


    @Override
    public void onModelContextUpdated(final EffectiveModelContext context) {
        final Module ietfYangLibraryModule = context.findModule(YangLibrary.QNAME.getModule()).orElse(null);
        if (ietfYangLibraryModule != null) {
            final YangLibrary newYangLibrary = createYangLibraryFromContext(context.getModules());
            final WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            tx.put(LogicalDatastoreType.OPERATIONAL, YANG_LIBRARY_INSTANCE_IDENTIFIER, newYangLibrary);

            LOG.debug("Trying to write new yang library: {}", newYangLibrary);
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

    private YangLibrary createYangLibraryFromContext(final Collection<? extends Module> modules) {
        ModuleSet modulesSet = new ModuleSetBuilder()
                .setName("state-modules")
                .setModule(modules.stream().map(this::createModuleEntryFromModule)
                        .collect(Collectors.toMap(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang
                                .library.rev190104.module.set.parameters.Module::key, Function.identity())))
                .build();

        Schema schema = new SchemaBuilder().setName("state-schema")
                .setModuleSet(Collections.singletonList(modulesSet.getName()))
                .build();

        Datastore datastore = new DatastoreBuilder().setName(Operational.class)
                .setSchema(schema.getName())
                .build();

        return new YangLibraryBuilder()
                .setModuleSet(ImmutableMap.of(modulesSet.key(), modulesSet))
                .setSchema(ImmutableMap.of(schema.key(), schema))
                .setDatastore(ImmutableMap.of(datastore.key(), datastore))
                .setContentId(String.valueOf(moduleSetId.getAndIncrement()))
                .build();
    }

    private org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.set
            .parameters.Module
        createModuleEntryFromModule(final Module module) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.set
                .parameters.ModuleBuilder()
                .setName(new YangIdentifier(module.getName() + "_"
                        + module.getRevision().map(Revision::toString).orElse(null)))
                .setRevision(ImportOnlyModuleRevisionBuilder.fromYangCommon(module.getQNameModule().getRevision())
                        .getRevisionIdentifier())
                .setNamespace(new Uri(module.getNamespace().toString()))
                .setSubmodule(createSubmodulesForModule(module))
                .build();
    }

    private static Map<SubmoduleKey, Submodule> createSubmodulesForModule(final Module module) {
        return module.getSubmodules().stream()
                .map(subModule -> new SubmoduleBuilder()
                    .setName(new YangIdentifier(subModule.getName()))
                    .setRevision(ImportOnlyModuleRevisionBuilder.fromYangCommon(subModule.getQNameModule()
                            .getRevision()).getRevisionIdentifier())
                    .build())
                .collect(Collectors.toMap(Submodule::key, Function.identity()));
    }
}
