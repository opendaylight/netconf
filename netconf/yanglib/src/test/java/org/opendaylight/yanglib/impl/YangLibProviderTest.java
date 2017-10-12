/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yanglib.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.ModulesStateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.OptionalRevision;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.RevisionIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.Module;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.ModuleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.ModuleKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.yanglib.impl.rev141210.YanglibConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.yanglib.impl.rev141210.YanglibConfigBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.api.YinSchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.parser.util.ASTSchemaSource;

public class YangLibProviderTest {
    private static final File CACHE_DIR = new File("target/yanglib");

    @Mock
    private DataBroker dataBroker;

    @Mock
    private WriteTransaction writeTransaction;

    private YangLibProvider yangLibProvider;

    @BeforeClass
    public static void staticSetup() {
        if (!CACHE_DIR.exists() && !CACHE_DIR.mkdirs()) {
            throw new RuntimeException("Failed to create " + CACHE_DIR);
        }
    }

    @AfterClass
    public static void staticCleanup() {
        FileUtils.deleteQuietly(CACHE_DIR);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        try {
            if (CACHE_DIR.exists()) {
                FileUtils.cleanDirectory(CACHE_DIR);
            }
        } catch (IOException e) {
            // Ignore
        }

        final YanglibConfig yanglibConfig = new YanglibConfigBuilder().setBindingAddr("www.fake.com")
                .setBindingPort(300L).setCacheFolder(CACHE_DIR.getAbsolutePath()).build();
        yangLibProvider = new YangLibProvider(yanglibConfig, dataBroker, new YangLibServiceImpl());
    }

    @Test
    public void testSchemaSourceRegistered() {
        yangLibProvider.init();
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(writeTransaction);
        doNothing().when(writeTransaction)
                .merge(eq(LogicalDatastoreType.OPERATIONAL), eq(InstanceIdentifier.create(ModulesState.class)), any());

        List<PotentialSchemaSource<?>> list = new ArrayList<>();
        list.add(
                PotentialSchemaSource.create(
                        RevisionSourceIdentifier.create("no-revision"),
                        YangTextSchemaSource.class, PotentialSchemaSource.Costs.IMMEDIATE.getValue()));

        list.add(
                PotentialSchemaSource.create(
                        RevisionSourceIdentifier.create("with-revision", Revision.of("2016-04-28")),
                        YangTextSchemaSource.class, PotentialSchemaSource.Costs.IMMEDIATE.getValue()));

        when(writeTransaction.submit()).thenReturn(Futures.immediateCheckedFuture(null));
        yangLibProvider.schemaSourceRegistered(list);

        List<Module> newModulesList = new ArrayList<>();

        Module newModule = new ModuleBuilder()
                .setName(new YangIdentifier("no-revision"))
                .setRevision(new OptionalRevision(""))
                .setSchema(new Uri("http://www.fake.com:300/yanglib/schemas/no-revision/"))
                .build();

        newModulesList.add(newModule);

        newModule = new ModuleBuilder()
                .setName(new YangIdentifier("with-revision"))
                .setRevision(new OptionalRevision(new RevisionIdentifier("2016-04-28")))
                .setSchema(new Uri("http://www.fake.com:300/yanglib/schemas/with-revision/2016-04-28"))
                .build();

        newModulesList.add(newModule);

        verify(dataBroker).newWriteOnlyTransaction();
        verify(writeTransaction).merge(eq(LogicalDatastoreType.OPERATIONAL),
                eq(InstanceIdentifier.create(ModulesState.class)),
                eq(new ModulesStateBuilder().setModule(newModulesList).build()));
        verify(writeTransaction).submit();
    }

    @Test
    public void testFilteringNonYangSchemaSourceRegistered() {
        yangLibProvider.init();

        // test empty list of schema sources registered
        List<PotentialSchemaSource<?>> potentialSources = Collections.emptyList();
        yangLibProvider.schemaSourceRegistered(potentialSources);

        verifyZeroInteractions(dataBroker, writeTransaction);

        // test list of non yang schema sources registered
        // expected behavior is to do nothing
        potentialSources = new ArrayList<>();
        potentialSources.add(
                PotentialSchemaSource.create(
                        RevisionSourceIdentifier.create("yin-source-representation"),
                        YinSchemaSourceRepresentation.class, PotentialSchemaSource.Costs.IMMEDIATE.getValue()));

        potentialSources.add(
                PotentialSchemaSource.create(
                        RevisionSourceIdentifier.create("asts-schema-source"),
                        ASTSchemaSource.class, PotentialSchemaSource.Costs.IMMEDIATE.getValue()));

        yangLibProvider.schemaSourceRegistered(potentialSources);
        verifyZeroInteractions(dataBroker, writeTransaction);

        // add yang schema source to list
        potentialSources.add(
                PotentialSchemaSource.create(
                        RevisionSourceIdentifier.create("yang-schema-source"),
                        YangTextSchemaSource.class, PotentialSchemaSource.Costs.IMMEDIATE.getValue()));

        when(dataBroker.newWriteOnlyTransaction()).thenReturn(writeTransaction);
        when(writeTransaction.submit()).thenReturn(Futures.immediateCheckedFuture(null));
        yangLibProvider.schemaSourceRegistered(potentialSources);
        verify(dataBroker).newWriteOnlyTransaction();

        ArgumentCaptor<ModulesState> modulesStateCaptor = ArgumentCaptor.forClass(ModulesState.class);
        verify(writeTransaction).merge(eq(LogicalDatastoreType.OPERATIONAL),
                eq(InstanceIdentifier.create(ModulesState.class)), modulesStateCaptor.capture());
        assertEquals(modulesStateCaptor.getValue().getModule().size(), 1);
        verify(writeTransaction).submit();
    }

    @Test
    public void testNonYangSchemaSourceUnregistered() {
        yangLibProvider.init();

        final PotentialSchemaSource<YinSchemaSourceRepresentation> nonYangSource =
                PotentialSchemaSource.create(
                        RevisionSourceIdentifier.create("yin-source-representation"),
                YinSchemaSourceRepresentation.class, PotentialSchemaSource.Costs.IMMEDIATE.getValue());

        yangLibProvider.schemaSourceUnregistered(nonYangSource);

        // expected behaviour is to do nothing if non yang based source is unregistered
        verifyZeroInteractions(dataBroker, writeTransaction);
    }

    @Test
    public void testSchemaSourceUnregistered() {
        yangLibProvider.init();

        when(dataBroker.newWriteOnlyTransaction()).thenReturn(writeTransaction);
        doNothing().when(writeTransaction)
                .delete(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class));

        when(writeTransaction.submit()).thenReturn(Futures.immediateCheckedFuture(null));

        PotentialSchemaSource<YangTextSchemaSource> yangUnregistererSource =
                PotentialSchemaSource.create(
                        RevisionSourceIdentifier.create("unregistered-yang-schema-without-revision"),
                        YangTextSchemaSource.class, PotentialSchemaSource.Costs.LOCAL_IO.getValue());

        yangLibProvider.schemaSourceUnregistered(yangUnregistererSource);

        verify(dataBroker).newWriteOnlyTransaction();
        verify(writeTransaction).delete(eq(LogicalDatastoreType.OPERATIONAL),
                eq(InstanceIdentifier.create(ModulesState.class)
                        .child(Module.class,
                                new ModuleKey(new YangIdentifier("unregistered-yang-schema-without-revision"),
                                        new OptionalRevision("")))));

        verify(writeTransaction).submit();

        yangUnregistererSource =
                PotentialSchemaSource.create(
                        RevisionSourceIdentifier.create("unregistered-yang-with-revision", Revision.of("2016-04-28")),
                        YangTextSchemaSource.class, PotentialSchemaSource.Costs.LOCAL_IO.getValue());

        yangLibProvider.schemaSourceUnregistered(yangUnregistererSource);

        verify(dataBroker, times(2)).newWriteOnlyTransaction();
        verify(writeTransaction).delete(eq(LogicalDatastoreType.OPERATIONAL),
                eq(InstanceIdentifier.create(ModulesState.class)
                        .child(Module.class,
                                new ModuleKey(new YangIdentifier("unregistered-yang-with-revision"),
                                        new OptionalRevision(new RevisionIdentifier("2016-04-28"))))));

        verify(writeTransaction, times(2)).submit();
    }
}
