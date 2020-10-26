/*
 * Copyright (c) 2020 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.yang.library.rfc8525;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.datastores.rev180214.Operational;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.RevisionIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibrary;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.set.parameters.ImportOnlyModuleRevisionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.set.parameters.Module;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.set.parameters.ModuleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.set.parameters.module.Submodule;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.set.parameters.module.SubmoduleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.Datastore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.DatastoreBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.ModuleSet;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.ModuleSetBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.Schema;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.SchemaBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class SchemaServiceToMdsalWriterTest {
    private static final InstanceIdentifier<YangLibrary> YANG_LIBRARY_INSTANCE_IDENTIFIER =
            InstanceIdentifier.create(YangLibrary.class);

    @Mock
    private DOMSchemaService schemaService;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private WriteTransaction writeTransaction;

    private SchemaServiceToMdsalWriter schemaServiceToMdsalWriter;

    @Before
    public void setUp() {
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(writeTransaction);
        doNothing().when(writeTransaction).put(eq(LogicalDatastoreType.OPERATIONAL), any(), any());
        doReturn(emptyFluentFuture()).when(writeTransaction).commit();
        when(schemaService.registerSchemaContextListener(any())).thenReturn(
                new ListenerRegistration<>() {
                    @Override
                    public void close() {

                    }

                    @Override
                    public EffectiveModelContextListener getInstance() {
                        return null;
                    }
                });
        schemaServiceToMdsalWriter = new SchemaServiceToMdsalWriter(schemaService, dataBroker);
    }

    @Test
    public void testOnGlobalContextUpdatedWithYangLibraryModule() {
        schemaServiceToMdsalWriter.start();

        schemaServiceToMdsalWriter.onModelContextUpdated(getSchema(true));
        verify(writeTransaction).put(eq(LogicalDatastoreType.OPERATIONAL),
                eq(YANG_LIBRARY_INSTANCE_IDENTIFIER), eq(createTestModuleSet()));
    }

    @Test
    public void testOnGlobalContextUpdatedWithoutYangLibraryModule() {
        schemaServiceToMdsalWriter.start();

        schemaServiceToMdsalWriter.onModelContextUpdated(getSchema(false));
        verify(writeTransaction, never()).put(any(), any(), any());
    }

    private static EffectiveModelContext getSchema(boolean withYangLibrary) {
        String[] modules = withYangLibrary
                ? new String[]{"/test-module.yang", "/test-submodule.yang", "/ietf-yang-library.yang"}
                : new String[]{"/test-module.yang", "/test-submodule.yang"};
        return YangParserTestUtils.parseYangResources(SchemaServiceToMdsalWriterTest.class, modules);
    }

    private static YangLibrary createTestModuleSet() {
        Submodule sub = new SubmoduleBuilder()
                .setName(new YangIdentifier("test-submodule"))
                .setRevision(ImportOnlyModuleRevisionBuilder.emptyRevision().getRevisionIdentifier())
                .build();

        Module modules = new ModuleBuilder().setName(new YangIdentifier("test-module_2013-07-22"))
                .setNamespace(new Uri("test:namespace"))
                .setRevision(new RevisionIdentifier("2013-07-22"))
                .setSubmodule(ImmutableMap.of(sub.key(), sub))
                .build();

        Module yangLibrary = new ModuleBuilder().setName(new YangIdentifier("ietf-yang-library_2019-01-04"))
                .setNamespace(new Uri("urn:ietf:params:xml:ns:yang:ietf-yang-library"))
                .setRevision(new RevisionIdentifier("2019-01-04"))
                .build();

        ModuleSet modulesSet = new ModuleSetBuilder()
                .setName("state-modules")
                .setModule(ImmutableMap.of(modules.key(), modules, yangLibrary.key(), yangLibrary))
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
                .setContentId("0")
                .build();
    }
}