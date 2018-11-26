/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.yang.library;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.ModulesStateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.RevisionUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.Module.ConformanceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.ModuleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.module.Submodule;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.module.SubmoduleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for updates on global schema context, transforms context to ietf-yang-library:modules-state and
 * writes this state to operational data store.
 */
// TODO Implement also yang-library-change notfication
public class SchemaServiceToMdsalWriter implements SchemaContextListener, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaServiceToMdsalWriter.class);

    private static final InstanceIdentifier<ModulesState> MODULES_STATE_INSTANCE_IDENTIFIER =
            InstanceIdentifier.create(ModulesState.class);

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
        // TODO Delete modules-state from operational data store
    }

    /**
     * Invoked by blueprint.
     */
    public void start() {
        schemaService.registerSchemaContextListener(this);
    }


    @Override
    public void onGlobalContextUpdated(final SchemaContext context) {
        final ModulesState newModuleState = createModuleStateFromModules(context.getModules());
        final WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL,
                MODULES_STATE_INSTANCE_IDENTIFIER, newModuleState);

        LOG.debug("Trying to write new module-state: {}", newModuleState);
        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Modules state updated successfully");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("Failed to update modules state", throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    private ModulesState createModuleStateFromModules(final Set<Module> modules) {
        return new ModulesStateBuilder()
                .setModule(modules.stream().map(this::createModuleEntryFromModule).collect(Collectors.toList()))
                .setModuleSetId(String.valueOf(moduleSetId.getAndIncrement()))
                .build();
    }

    private org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.Module
        createModuleEntryFromModule(final Module module) {
        return new ModuleBuilder()
                .setName(new YangIdentifier(module.getName()))
                .setRevision(RevisionUtils.fromYangCommon(module.getQNameModule().getRevision()))
                .setNamespace(new Uri(module.getNamespace().toString()))
                // FIXME: Conformance type is always set to Implement value, but it should it really be like this?
                .setConformanceType(ConformanceType.Implement)
                .setSubmodule(createSubmodulesForModule(module))
                // FIXME: Add also deviations and features lists to module entries
                .build();
    }

    private static List<Submodule> createSubmodulesForModule(final Module module) {
        return module.getSubmodules().stream().map(subModule -> new SubmoduleBuilder()
            .setName(new YangIdentifier(subModule.getName()))
            .setRevision(RevisionUtils.fromYangCommon(subModule.getQNameModule().getRevision()))
            .build()).collect(Collectors.toList());
    }
}
