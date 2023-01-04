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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import org.opendaylight.yangtools.yang.binding.util.BindingMap;
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
    @Mock
    private DataBroker dataBroker;
    @Mock
    private WriteTransaction writeTransaction;

    private YangLibProvider yangLibProvider;

    @Before
    public void setUp() {
        doReturn(emptyFluentFuture()).when(writeTransaction).commit();
        doReturn(writeTransaction).when(dataBroker).newWriteOnlyTransaction();

        yangLibProvider = new YangLibProvider(dataBroker, new DefaultYangParserFactory(),
            YangLibProviderTest.class.getResource("/model").getPath(), "www.fake.com", Uint32.valueOf(300));
    }

    @Test
    public void testSchemaSourceRegistered() {
        // test that initial models are registered
        verify(dataBroker).newWriteOnlyTransaction();
        verify(writeTransaction).merge(eq(LogicalDatastoreType.OPERATIONAL),
            eq(InstanceIdentifier.create(ModulesState.class)),
            eq(new ModulesStateBuilder()
                .setModule(BindingMap.of(new ModuleBuilder()
                    .setName(new YangIdentifier("model1"))
                    .setRevision(new Revision(new RevisionIdentifier("2023-02-21")))
                    .setSchema(new Uri("http://www.fake.com:300/yanglib/schemas/model1/2023-02-21"))
                    .build(), new ModuleBuilder()
                    .setName(new YangIdentifier("model2"))
                    .setRevision(LegacyRevisionUtils.emptyRevision())
                    .setSchema(new Uri("http://www.fake.com:300/yanglib/schemas/model2"))
                    .build()))
                .build()));
        verify(writeTransaction).commit();
    }

    @Test
    public void testFilteringEmptySchemaSourceRegistered() {
        clearInvocations(dataBroker, writeTransaction);

        // test empty list of schema sources registered
        yangLibProvider.schemaSourceRegistered(List.of());
        // expected behavior is to do nothing
        verifyNoMoreInteractions(dataBroker, writeTransaction);
    }

    @Test
    public void testFilteringNonYangSchemaSourceRegistered() {
        clearInvocations(dataBroker, writeTransaction);

        // test list of non yang schema sources registered
        yangLibProvider.schemaSourceRegistered(List.of(
            PotentialSchemaSource.create(new SourceIdentifier("yin-source-representation"),
                YinSchemaSourceRepresentation.class, Costs.IMMEDIATE.getValue()),
            PotentialSchemaSource.create(new SourceIdentifier("asts-schema-source"),
                YangIRSchemaSource.class, Costs.IMMEDIATE.getValue())));

        // expected behavior is to do nothing
        verifyNoMoreInteractions(dataBroker, writeTransaction);
    }

    @Test
    public void testSchemaSourceWithRevisionUnregistered() {
        clearInvocations(dataBroker, writeTransaction);

        // try to unregister YANG source with revision
        final var schemaSourceWithRevision = PotentialSchemaSource.create(
            new SourceIdentifier("unregistered-yang-with-revision", "2016-04-28"),
            YangTextSchemaSource.class, Costs.LOCAL_IO.getValue());
        yangLibProvider.schemaSourceUnregistered(schemaSourceWithRevision);

        // source is unregistered
        verify(dataBroker).newWriteOnlyTransaction();
        verify(writeTransaction).delete(eq(LogicalDatastoreType.OPERATIONAL),
            eq(InstanceIdentifier.create(ModulesState.class)
                .child(Module.class,
                    new ModuleKey(new YangIdentifier("unregistered-yang-with-revision"),
                        new Revision(new RevisionIdentifier("2016-04-28"))))));
        verify(writeTransaction).commit();
    }

    @Test
    public void testSchemaSourceWithoutRevisionUnregistered() {
        clearInvocations(dataBroker, writeTransaction);

        // try to unregister YANG source without revision
        final var schemaSourceWithoutRevision = PotentialSchemaSource.create(
            new SourceIdentifier("unregistered-yang-schema-without-revision"), YangTextSchemaSource.class,
            Costs.LOCAL_IO.getValue());
        yangLibProvider.schemaSourceUnregistered(schemaSourceWithoutRevision);

        // source is unregistered
        verify(dataBroker).newWriteOnlyTransaction();
        verify(writeTransaction).delete(eq(LogicalDatastoreType.OPERATIONAL),
            eq(InstanceIdentifier.create(ModulesState.class)
                .child(Module.class,
                    new ModuleKey(new YangIdentifier("unregistered-yang-schema-without-revision"),
                        LegacyRevisionUtils.emptyRevision()))));
        verify(writeTransaction).commit();
    }

    @Test
    public void testNonYangSchemaSourceUnregistered() {
        clearInvocations(dataBroker, writeTransaction);

        // try to unregister non-YANG source
        final var nonYangSources = PotentialSchemaSource.create(new SourceIdentifier("yin-source-representation"),
            YinSchemaSourceRepresentation.class, Costs.IMMEDIATE.getValue());
        yangLibProvider.schemaSourceUnregistered(nonYangSources);

        // expected behaviour is to do nothing if non yang based source is unregistered
        verifyNoMoreInteractions(dataBroker, writeTransaction);
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
