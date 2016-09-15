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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import java.io.InputStream;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.ModulesStateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.OptionalRevision;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.Module;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.ModuleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.module.SubmodulesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.module.submodules.Submodule;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.module.submodules.SubmoduleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.YangInferencePipeline;

public class SchemaServiceToMdsalWriterTest {

    private static final InstanceIdentifier<ModulesState> MODULES_STATE_INSTANCE_IDENTIFIER =
            InstanceIdentifier.create(ModulesState.class);

    @Mock
    private SchemaService schemaService;
    @Mock
    private BindingAwareBroker.ProviderContext context;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private WriteTransaction writeTransaction;

    private SchemaServiceToMdsalWriter schemaServiceToMdsalWriter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(context.getSALService(DataBroker.class)).thenReturn(dataBroker);
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(writeTransaction);
        doNothing().when(writeTransaction).put(eq(LogicalDatastoreType.OPERATIONAL), any(), any());
        when(writeTransaction.submit()).thenReturn(Futures.immediateCheckedFuture(null));
        when(schemaService.registerSchemaContextListener(any())).thenReturn(new ListenerRegistration<SchemaContextListener>() {
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
    public void testOnGlobalContextUpdated() {
        schemaServiceToMdsalWriter.start();

        schemaServiceToMdsalWriter.onGlobalContextUpdated(getSchema());
        verify(writeTransaction).put(eq(LogicalDatastoreType.OPERATIONAL), eq(MODULES_STATE_INSTANCE_IDENTIFIER), eq(createTestModuleState()));
    }

    private SchemaContext getSchema() {
        final List<InputStream> modelsToParse = Lists.newArrayList(
                SchemaServiceToMdsalWriterTest.class.getResourceAsStream("/test-module.yang"),
                SchemaServiceToMdsalWriterTest.class.getResourceAsStream("/test-submodule.yang")
        );
        return parseYangStreams(modelsToParse);
    }

    private SchemaContext parseYangStreams(final List<InputStream> streams) {
        CrossSourceStatementReactor.BuildAction reactor = YangInferencePipeline.RFC6020_REACTOR
                .newBuild();
        final SchemaContext schemaContext;
        try {
            schemaContext = reactor.buildEffective(streams);
        } catch (ReactorException e) {
            throw new RuntimeException("Unable to build schema context from " + streams, e);
        }
        return schemaContext;
    }

    private ModulesState createTestModuleState() {
        Submodule sub = new SubmoduleBuilder().setName(new YangIdentifier("test-submodule"))
                .setRevision(new OptionalRevision("1970-01-01"))
                .build();

        Module module = new ModuleBuilder().setName(new YangIdentifier("test-module"))
                .setNamespace(new Uri("test:namespace"))
                .setRevision(new OptionalRevision("2013-07-22"))
                .setSubmodules(new SubmodulesBuilder().setSubmodule(Lists.newArrayList(sub)).build())
                .setConformanceType(Module.ConformanceType.Implement)
                .build();
        return  new ModulesStateBuilder().setModuleSetId("0")
                .setModule(Lists.newArrayList(module)).build();
    }
}