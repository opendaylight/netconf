/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.yang.library;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.datastores.rev180214.Operational;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.LegacyRevisionUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesStateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.RevisionUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibrary;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module.ConformanceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.set.parameters.module.SubmoduleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.DatastoreBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.ModuleSetBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.SchemaBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.util.BindingMap;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.model.api.FeatureDefinition;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleLike;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for updates on global schema context, transforms context to ietf-yang-library/yang-library and writes this
 * state to operational data store.
 */
@Singleton
@Component(immediate = true, service = {})
public final class YangLibraryWriter implements EffectiveModelContextListener, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(YangLibraryWriter.class);
    private static final String MODULE_SET_NAME = "state-modules";
    private static final String SCHEMA_NAME = "state-schema";

    private static final InstanceIdentifier<YangLibrary> YANG_LIBRARY_INSTANCE_IDENTIFIER =
        InstanceIdentifier.create(YangLibrary.class);
    @Deprecated
    private static final InstanceIdentifier<ModulesState> MODULES_STATE_INSTANCE_IDENTIFIER =
        InstanceIdentifier.create(ModulesState.class);

    private final DataBroker dataBroker;
    @GuardedBy("this")
    private long moduleSetId;
    @GuardedBy("this")
    private Registration reg;

    @Inject
    @Activate
    public YangLibraryWriter(final @Reference DOMSchemaService schemaService,
            final @Reference DataBroker dataBroker) {
        this.dataBroker = requireNonNull(dataBroker);
        reg = schemaService.registerSchemaContextListener(this);
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
        final WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, YANG_LIBRARY_INSTANCE_IDENTIFIER);
        tx.delete(LogicalDatastoreType.OPERATIONAL, MODULES_STATE_INSTANCE_IDENTIFIER);

        final FluentFuture<? extends CommitInfo> future = tx.commit();
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

    @Override
    public void onModelContextUpdated(final EffectiveModelContext context) {
        final Module ietfYangLibraryModule = context.findModule(YangLibrary.QNAME.getModule()).orElse(null);
        if (ietfYangLibraryModule != null) {
            updateYangLibrary(context);
        } else {
            LOG.debug("ietf-yang-library not present in context, skipping update");
        }
    }

    private synchronized void updateYangLibrary(final EffectiveModelContext context) {
        if (reg == null) {
            // Already shut down, do not do anything
            return;
        }

        final long currentSetId = moduleSetId++;

        final YangLibrary newYangLibrary = createYangLibraryFromContext(context.getModules(), currentSetId);
        final ModulesState newModuleState = createModuleStateFromModules(context.getModules(), currentSetId);
        LOG.debug("Trying to write new yang-library: {}", newYangLibrary);
        LOG.debug("Trying to write new module-state: {}", newModuleState);

        final WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, YANG_LIBRARY_INSTANCE_IDENTIFIER, newYangLibrary);
        tx.put(LogicalDatastoreType.OPERATIONAL, MODULES_STATE_INSTANCE_IDENTIFIER, newModuleState);
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

    private static YangLibrary createYangLibraryFromContext(final Collection<? extends Module> modules,
            final long moduleSetId) {
        final var moduleMap = modules.stream()
            .map(module -> {
                final var submoduleMap = module.getSubmodules().stream()
                    .map(subModule -> new SubmoduleBuilder()
                        .setName(new YangIdentifier(subModule.getName()))
                        .setRevision(RevisionUtils.fromYangCommon(subModule.getQNameModule().getRevision())
                            .getRevisionIdentifier())
                        .build())
                    .collect(BindingMap.toMap());

                return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library
                    .rev190104.module.set.parameters.ModuleBuilder()
                    .setName(new YangIdentifier(module.getName() + "_"
                        // FIXME: 'orElse' seems to be wrong here
                        + module.getRevision().map(Revision::toString).orElse(null)))
                    .setRevision(RevisionUtils.fromYangCommon(module.getQNameModule().getRevision())
                        .getRevisionIdentifier())
                    .setNamespace(new Uri(module.getNamespace().toString()))
                    .setFeature(extractFeatures(module))
                    // FIXME: inline this once it's disambiguated
                    .setSubmodule(submoduleMap)
                    .build();
            })
            .collect(BindingMap.toMap());

        return new YangLibraryBuilder()
            .setModuleSet(BindingMap.of(new ModuleSetBuilder()
                .setName(MODULE_SET_NAME)
                // FIXME: inline this once it's disambiguated
                .setModule(moduleMap)
                .build()))
            .setSchema(BindingMap.of(new SchemaBuilder()
                .setName(SCHEMA_NAME)
                .setModuleSet(Set.of(MODULE_SET_NAME))
                .build()))
            .setDatastore(BindingMap.of(new DatastoreBuilder()
                .setName(Operational.VALUE)
                .setSchema(SCHEMA_NAME)
                .build()))
            .setContentId(String.valueOf(moduleSetId))
            .build();
    }

    @Deprecated
    private static ModulesState createModuleStateFromModules(final Collection<? extends Module> modules,
            final long moduleSetId) {
        final var moduleMap = modules.stream()
            .map(module -> {
                final var submoduleMap = module.getSubmodules().stream()
                    .map(subModule -> new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library
                        .rev190104.module.list.module.SubmoduleBuilder()
                        .setName(new YangIdentifier(subModule.getName()))
                        .setRevision(LegacyRevisionUtils.fromYangCommon(subModule.getQNameModule()
                            .getRevision()))
                        .build())
                    .collect(BindingMap.toMap());

                return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library
                    .rev190104.module.list.ModuleBuilder()
                    .setName(new YangIdentifier(module.getName()))
                    .setRevision(LegacyRevisionUtils.fromYangCommon(module.getQNameModule().getRevision()))
                    .setNamespace(new Uri(module.getNamespace().toString()))
                    // FIXME: Conformance type is always set to Implement value, but it should it really be like this?
                    .setConformanceType(ConformanceType.Implement)
                    // FIXME: inline this once it's disambiguated
                    .setSubmodule(submoduleMap)
                    .setFeature(extractFeatures(module))
                    // FIXME: Add also deviations to module entries
                    .build();
            })
            .collect(BindingMap.toMap());

        return new ModulesStateBuilder()
            // FIXME: inline this once it's disambiguated
            .setModule(moduleMap)
            .setModuleSetId(String.valueOf(moduleSetId))
            .build();
    }

    private static Set<YangIdentifier> extractFeatures(final ModuleLike module) {
        final var namespace = module.getQNameModule();

        return module.getFeatures().stream()
            .map(FeatureDefinition::getQName)
            // belt-and-suspenders: make sure the feature namespace matches
            .filter(featureName -> namespace.equals(featureName.getModule()))
            .map(featureName -> new YangIdentifier(featureName.getLocalName()))
            .collect(Collectors.toUnmodifiableSet());
    }
}
