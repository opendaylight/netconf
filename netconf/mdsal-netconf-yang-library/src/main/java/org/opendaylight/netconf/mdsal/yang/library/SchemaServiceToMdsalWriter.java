/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.yang.library;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.ModulesStateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.OptionalRevision;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.Module.ConformanceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.ModuleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.module.Submodules;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.module.SubmodulesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.module.submodules.Submodule;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.module.submodules.SubmoduleBuilder;
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
// TODO Implement also yang-library-change notification
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
    public void close() throws Exception {
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
        Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable final Void result) {
                LOG.debug("Modules state updated successfully");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("Failed to update modules state", throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    private ModulesState createModuleStateFromModules(final Set<Module> modules) {
        final ModulesStateBuilder modulesStateBuilder = new ModulesStateBuilder();
        final List<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list
                .Module> moduleList =
                Lists.newArrayList();

        for (final Module module : modules) {
            moduleList.add(createModuleEntryFromModule(module));
        }

        return modulesStateBuilder.setModule(moduleList).setModuleSetId(String.valueOf(moduleSetId.getAndIncrement()))
                .build();
    }

    private static
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.Module
        createModuleEntryFromModule(final Module module) {
        final ModuleBuilder moduleBuilder = new ModuleBuilder();

        // TODO Conformance type is always set to Implement value, but it should it really be like this?
        // TODO Add also deviations and features lists to module entries
        moduleBuilder.setName(new YangIdentifier(module.getName()))
                .setRevision(new OptionalRevision(module.getQNameModule().getFormattedRevision()))
                .setNamespace(new Uri(module.getNamespace().toString()))
                .setConformanceType(ConformanceType.Implement)
                .setSubmodules(createSubmodulesForModule(module));

        return moduleBuilder.build();
    }

    private static Submodules createSubmodulesForModule(final Module module) {
        final List<Submodule> submodulesList = Lists.newArrayList();
        for (final Module subModule : module.getSubmodules()) {
            final SubmoduleBuilder subModuleEntryBuilder = new SubmoduleBuilder();
            subModuleEntryBuilder.setName(new YangIdentifier(subModule.getName()))
                    .setRevision(new OptionalRevision(subModule.getQNameModule().getFormattedRevision()));
            submodulesList.add(subModuleEntryBuilder.build());
        }

        return new SubmodulesBuilder().setSubmodule(submodulesList).build();
    }
}
