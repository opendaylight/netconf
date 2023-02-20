/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yanglib.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import org.junit.Before;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.yanglib.impl.rev141210.YanglibConfigBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangIRSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.api.YinSchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource.Costs;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class YangLibProviderTest {
    private static final File CACHE_DIR = new File(YangLibProvider.class.getResource("/model").getFile());

    @Mock
    private DataBroker dataBroker;
    @Mock
    private WriteTransaction writeTransaction;

    private YangLibProvider yangLibProvider;

    @Before
    public void setUp() {
        final var yanglibConfig = new YanglibConfigBuilder().setBindingAddr("www.fake.com")
            .setBindingPort(Uint32.valueOf(300)).setCacheFolder(CACHE_DIR.getAbsolutePath()).build();
        yangLibProvider = new YangLibProvider(yanglibConfig, dataBroker, new DefaultYangParserFactory());

        doNothing().when(writeTransaction).delete(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class));
        doNothing().when(writeTransaction).merge(eq(LogicalDatastoreType.OPERATIONAL),
            eq(InstanceIdentifier.create(ModulesState.class)), any());
        doReturn(emptyFluentFuture()).when(writeTransaction).commit();
        doReturn(writeTransaction).when(dataBroker).newWriteOnlyTransaction();

        yangLibProvider.init();
    }

    @Test
    public void testSchemaSourceRegistered() {
        final var potentialSchemaSources = new ArrayList<PotentialSchemaSource<?>>();
        potentialSchemaSources.add(PotentialSchemaSource.create(new SourceIdentifier("no-revision"),
            YangTextSchemaSource.class, Costs.IMMEDIATE.getValue()));
        potentialSchemaSources.add(PotentialSchemaSource.create(new SourceIdentifier("ietf-netconf", "2011-06-01"),
            YangTextSchemaSource.class, Costs.IMMEDIATE.getValue()));
        yangLibProvider.schemaSourceRegistered(potentialSchemaSources);

        final var newModulesMap = new HashMap<ModuleKey, Module>();
        final var noRevisionModule = new ModuleBuilder()
            .setName(new YangIdentifier("no-revision"))
            .setRevision(LegacyRevisionUtils.emptyRevision())
            .setSchema(new Uri("http://www.fake.com:300/yanglib/schemas/no-revision"))
            .build();
        newModulesMap.put(noRevisionModule.key(), noRevisionModule);

        final var ietfNetconfModule = new ModuleBuilder()
            .setName(new YangIdentifier("ietf-netconf"))
            .setRevision(new Revision(new RevisionIdentifier("2011-06-01")))
            .setSchema(new Uri("http://www.fake.com:300/yanglib/schemas/ietf-netconf/2011-06-01"))
            .build();
        newModulesMap.put(ietfNetconfModule.key(), ietfNetconfModule);

        verify(dataBroker, times(2)).newWriteOnlyTransaction();
        verify(writeTransaction).merge(eq(LogicalDatastoreType.OPERATIONAL),
            eq(InstanceIdentifier.create(ModulesState.class)),
            eq(new ModulesStateBuilder().setModule(newModulesMap).build()));
        verify(writeTransaction, times(2)).commit();
    }

    @Test
    public void testFilteringNonYangSchemaSourceRegistered() {
        // test empty list of schema sources registered
        yangLibProvider.schemaSourceRegistered(Collections.emptyList());
        verifyNoMoreInteractions(dataBroker, writeTransaction);

        // test list of non yang schema sources registered
        // expected behavior is to do nothing
        final var potentialSources = new ArrayList<PotentialSchemaSource<?>>();
        potentialSources.add(PotentialSchemaSource.create(new SourceIdentifier("yin-source-representation"),
            YinSchemaSourceRepresentation.class, Costs.IMMEDIATE.getValue()));
        potentialSources.add(PotentialSchemaSource.create(new SourceIdentifier("asts-schema-source"),
            YangIRSchemaSource.class, Costs.IMMEDIATE.getValue()));

        yangLibProvider.schemaSourceRegistered(potentialSources);
        verifyNoMoreInteractions(dataBroker, writeTransaction);

        // add yang schema source to list
        potentialSources.add(PotentialSchemaSource.create(new SourceIdentifier("yang-schema-source"),
            YangTextSchemaSource.class, Costs.IMMEDIATE.getValue()));

        yangLibProvider.schemaSourceRegistered(potentialSources);
        verify(dataBroker, times(2)).newWriteOnlyTransaction();

        final var modulesStateCaptor = ArgumentCaptor.forClass(ModulesState.class);
        verify(writeTransaction, times(2)).merge(eq(LogicalDatastoreType.OPERATIONAL),
            eq(InstanceIdentifier.create(ModulesState.class)), modulesStateCaptor.capture());
        assertEquals(modulesStateCaptor.getValue().getModule().size(), 1);
        verify(writeTransaction, times(2)).commit();
    }

    @Test
    public void testNonYangSchemaSourceUnregistered() {
        final var nonYangSource = PotentialSchemaSource.create(new SourceIdentifier("yin-source-representation"),
            YinSchemaSourceRepresentation.class, Costs.IMMEDIATE.getValue());
        yangLibProvider.schemaSourceUnregistered(nonYangSource);

        // expected behaviour is to do nothing if non yang based source is unregistered
        verifyNoMoreInteractions(dataBroker, writeTransaction);
    }

    @Test
    public void testSchemaSourceUnregistered() {
        final var schemaSourceWithoutRevision = PotentialSchemaSource.create(
            new SourceIdentifier("unregistered-yang-schema-without-revision"), YangTextSchemaSource.class,
            Costs.LOCAL_IO.getValue());
        yangLibProvider.schemaSourceUnregistered(schemaSourceWithoutRevision);

        verify(dataBroker, times(2)).newWriteOnlyTransaction();
        verify(writeTransaction).delete(eq(LogicalDatastoreType.OPERATIONAL),
            eq(InstanceIdentifier.create(ModulesState.class)
                .child(Module.class,
                    new ModuleKey(new YangIdentifier("unregistered-yang-schema-without-revision"),
                        LegacyRevisionUtils.emptyRevision()))));
        verify(writeTransaction, times(2)).commit();

        final var schemaSourceWithRevision = PotentialSchemaSource.create(
            new SourceIdentifier("unregistered-yang-with-revision", "2016-04-28"),
            YangTextSchemaSource.class, Costs.LOCAL_IO.getValue());
        yangLibProvider.schemaSourceUnregistered(schemaSourceWithRevision);

        verify(dataBroker, times(3)).newWriteOnlyTransaction();
        verify(writeTransaction).delete(eq(LogicalDatastoreType.OPERATIONAL),
            eq(InstanceIdentifier.create(ModulesState.class)
                .child(Module.class,
                    new ModuleKey(new YangIdentifier("unregistered-yang-with-revision"),
                        new Revision(new RevisionIdentifier("2016-04-28"))))));
        verify(writeTransaction, times(3)).commit();
    }

    @Test
    public void testGetSchemaWithRevision() {
        final var modelWithRevision = yangLibProvider.getSchema("model1", "2023-02-21");
        assertNotNull(modelWithRevision);
        assertEquals("""
            module model1 {
              namespace "model:with:revision";
              prefix mwr;

              revision 2023-02-21 {
                description
                  "Initial revision;";
              }

              container test {
                leaf test-leaf {
                  type string;
                }
              }
            }
            """, modelWithRevision);
    }

    @Test
    public void testGetSchemaWithoutRevision() {
        final var modelWithoutRevision = yangLibProvider.getSchema("model2");
        assertNotNull(modelWithoutRevision);
        assertEquals("""
            module model2 {
              namespace "model:with:no:revision";
              prefix mwnr;

              container test {
                leaf test-leaf {
                  type string;
                }
              }
            }
            """, modelWithoutRevision);
    }
}
