/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.yang.library;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.ModulesStateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.RevisionIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.RevisionUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.CommonLeafs.Revision;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.Module;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.ModuleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.module.Submodule;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.module.SubmoduleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class SchemaServiceToMdsalWriterTest {

    private static final InstanceIdentifier<ModulesState> MODULES_STATE_INSTANCE_IDENTIFIER =
            InstanceIdentifier.create(ModulesState.class);

    @Mock
    private DOMSchemaService schemaService;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private WriteTransaction writeTransaction;

    private SchemaServiceToMdsalWriter schemaServiceToMdsalWriter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(dataBroker.newWriteOnlyTransaction()).thenReturn(writeTransaction);
        doNothing().when(writeTransaction).put(eq(LogicalDatastoreType.OPERATIONAL), any(), any());
        doReturn(emptyFluentFuture()).when(writeTransaction).commit();
        when(schemaService.registerSchemaContextListener(any())).thenReturn(
                new ListenerRegistration<SchemaContextListener>() {
                    @Override
                    public void close() {

                    }

                    @Override
                    public SchemaContextListener getInstance() {
                        return null;
                    }
                });
        schemaServiceToMdsalWriter = new SchemaServiceToMdsalWriter(schemaService, dataBroker);
    }

    @Test
    public void testOnGlobalContextUpdated() throws Exception {
        schemaServiceToMdsalWriter.start();

        schemaServiceToMdsalWriter.onGlobalContextUpdated(getSchema());
        verify(writeTransaction).put(eq(LogicalDatastoreType.OPERATIONAL),
                eq(MODULES_STATE_INSTANCE_IDENTIFIER), eq(createTestModuleState()));
    }

    private static SchemaContext getSchema() {
        return YangParserTestUtils.parseYangResources(SchemaServiceToMdsalWriterTest.class, "/test-module.yang",
            "/test-submodule.yang");
    }

    private static ModulesState createTestModuleState() {
        Submodule sub = new SubmoduleBuilder().setName(new YangIdentifier("test-submodule"))
                .setRevision(RevisionUtils.emptyRevision())
                .build();

        Module module = new ModuleBuilder().setName(new YangIdentifier("test-module"))
                .setNamespace(new Uri("test:namespace"))
                .setRevision(new Revision(new RevisionIdentifier("2013-07-22")))
                .setSubmodule(ImmutableList.of(sub))
                .setConformanceType(Module.ConformanceType.Implement)
                .build();
        return new ModulesStateBuilder().setModuleSetId("0")
                .setModule(ImmutableList.of(module)).build();
    }
}
