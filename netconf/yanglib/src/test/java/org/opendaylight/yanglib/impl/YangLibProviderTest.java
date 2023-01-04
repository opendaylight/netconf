/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yanglib.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.LegacyRevisionUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesStateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.RevisionIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.CommonLeafs.Revision;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.ModuleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.ModuleKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangIRSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.api.YinSchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
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
        try {
            if (CACHE_DIR.exists()) {
                FileUtils.cleanDirectory(CACHE_DIR);
            }
        } catch (IOException e) {
            // Ignore
        }

        yangLibProvider = new YangLibProvider(dataBroker, new DefaultYangParserFactory(), CACHE_DIR.getAbsolutePath(),
                "www.fake.com", Uint32.valueOf(300));
    }

    @Test
    public void testSchemaSourceRegistered() {
        yangLibProvider.init();
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(writeTransaction);
        doNothing().when(writeTransaction)
                .merge(eq(LogicalDatastoreType.OPERATIONAL), eq(InstanceIdentifier.create(ModulesState.class)), any());

        List<PotentialSchemaSource<?>> list = new ArrayList<>();
        list.add(
                PotentialSchemaSource.create(new SourceIdentifier("no-revision"),
                        YangTextSchemaSource.class, PotentialSchemaSource.Costs.IMMEDIATE.getValue()));

        list.add(
                PotentialSchemaSource.create(new SourceIdentifier("with-revision", "2016-04-28"),
                        YangTextSchemaSource.class, PotentialSchemaSource.Costs.IMMEDIATE.getValue()));

        doReturn(emptyFluentFuture()).when(writeTransaction).commit();
        yangLibProvider.schemaSourceRegistered(list);

        Map<ModuleKey, Module> newModulesList = new HashMap<>();

        Module newModule = new ModuleBuilder()
                .setName(new YangIdentifier("no-revision"))
                .setRevision(LegacyRevisionUtils.emptyRevision())
                .setSchema(new Uri("http://www.fake.com:300/yanglib/schemas/no-revision/"))
                .build();

        newModulesList.put(newModule.key(), newModule);

        newModule = new ModuleBuilder()
                .setName(new YangIdentifier("with-revision"))
                .setRevision(new Revision(new RevisionIdentifier("2016-04-28")))
                .setSchema(new Uri("http://www.fake.com:300/yanglib/schemas/with-revision/2016-04-28"))
                .build();

        newModulesList.put(newModule.key(), newModule);

        verify(dataBroker).newWriteOnlyTransaction();
        verify(writeTransaction).merge(eq(LogicalDatastoreType.OPERATIONAL),
                eq(InstanceIdentifier.create(ModulesState.class)),
                eq(new ModulesStateBuilder().setModule(newModulesList).build()));
        verify(writeTransaction).commit();
    }

    @Test
    public void testFilteringNonYangSchemaSourceRegistered() {
        yangLibProvider.init();

        // test empty list of schema sources registered
        List<PotentialSchemaSource<?>> potentialSources = Collections.emptyList();
        yangLibProvider.schemaSourceRegistered(potentialSources);

        verifyNoMoreInteractions(dataBroker, writeTransaction);

        // test list of non yang schema sources registered
        // expected behavior is to do nothing
        potentialSources = new ArrayList<>();
        potentialSources.add(
                PotentialSchemaSource.create(new SourceIdentifier("yin-source-representation"),
                        YinSchemaSourceRepresentation.class, PotentialSchemaSource.Costs.IMMEDIATE.getValue()));

        potentialSources.add(
                PotentialSchemaSource.create(new SourceIdentifier("asts-schema-source"),
                        YangIRSchemaSource.class, PotentialSchemaSource.Costs.IMMEDIATE.getValue()));

        yangLibProvider.schemaSourceRegistered(potentialSources);
        verifyNoMoreInteractions(dataBroker, writeTransaction);

        // add yang schema source to list
        potentialSources.add(
                PotentialSchemaSource.create(new SourceIdentifier("yang-schema-source"),
                        YangTextSchemaSource.class, PotentialSchemaSource.Costs.IMMEDIATE.getValue()));

        when(dataBroker.newWriteOnlyTransaction()).thenReturn(writeTransaction);
        doReturn(emptyFluentFuture()).when(writeTransaction).commit();
        yangLibProvider.schemaSourceRegistered(potentialSources);
        verify(dataBroker).newWriteOnlyTransaction();

        ArgumentCaptor<ModulesState> modulesStateCaptor = ArgumentCaptor.forClass(ModulesState.class);
        verify(writeTransaction).merge(eq(LogicalDatastoreType.OPERATIONAL),
                eq(InstanceIdentifier.create(ModulesState.class)), modulesStateCaptor.capture());
        assertEquals(modulesStateCaptor.getValue().getModule().size(), 1);
        verify(writeTransaction).commit();
    }

    @Test
    public void testNonYangSchemaSourceUnregistered() {
        yangLibProvider.init();

        final PotentialSchemaSource<YinSchemaSourceRepresentation> nonYangSource =
            PotentialSchemaSource.create(new SourceIdentifier("yin-source-representation"),
                YinSchemaSourceRepresentation.class, PotentialSchemaSource.Costs.IMMEDIATE.getValue());

        yangLibProvider.schemaSourceUnregistered(nonYangSource);

        // expected behaviour is to do nothing if non yang based source is unregistered
        verifyNoMoreInteractions(dataBroker, writeTransaction);
    }

    @Test
    public void testSchemaSourceUnregistered() {
        yangLibProvider.init();

        when(dataBroker.newWriteOnlyTransaction()).thenReturn(writeTransaction);
        doNothing().when(writeTransaction)
                .delete(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class));

        doReturn(emptyFluentFuture()).when(writeTransaction).commit();

        PotentialSchemaSource<YangTextSchemaSource> yangUnregistererSource =
            PotentialSchemaSource.create(new SourceIdentifier("unregistered-yang-schema-without-revision"),
                YangTextSchemaSource.class, PotentialSchemaSource.Costs.LOCAL_IO.getValue());

        yangLibProvider.schemaSourceUnregistered(yangUnregistererSource);

        verify(dataBroker).newWriteOnlyTransaction();
        verify(writeTransaction).delete(eq(LogicalDatastoreType.OPERATIONAL),
                eq(InstanceIdentifier.create(ModulesState.class)
                        .child(Module.class,
                                new ModuleKey(new YangIdentifier("unregistered-yang-schema-without-revision"),
                                        LegacyRevisionUtils.emptyRevision()))));

        verify(writeTransaction).commit();

        yangUnregistererSource =
                PotentialSchemaSource.create(new SourceIdentifier("unregistered-yang-with-revision", "2016-04-28"),
                        YangTextSchemaSource.class, PotentialSchemaSource.Costs.LOCAL_IO.getValue());

        yangLibProvider.schemaSourceUnregistered(yangUnregistererSource);

        verify(dataBroker, times(2)).newWriteOnlyTransaction();
        verify(writeTransaction).delete(eq(LogicalDatastoreType.OPERATIONAL),
                eq(InstanceIdentifier.create(ModulesState.class)
                        .child(Module.class,
                                new ModuleKey(new YangIdentifier("unregistered-yang-with-revision"),
                                        new Revision(new RevisionIdentifier("2016-04-28"))))));

        verify(writeTransaction, times(2)).commit();
    }
}
