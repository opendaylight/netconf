/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.yanglib.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.OPERATIONAL;
import static org.opendaylight.netconf.yanglib.writer.YangLibraryContentBuilderUtil.DEFAULT_MODULE_SET_NAME;
import static org.opendaylight.netconf.yanglib.writer.YangLibraryContentBuilderUtil.DEFAULT_SCHEMA_NAME;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module.ConformanceType.Implement;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module.ConformanceType.Import;
import static org.opendaylight.yangtools.yang.test.util.YangParserTestUtils.parseYangResources;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.datastores.rev180214.Operational;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.LegacyRevisionUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesStateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.RevisionIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.RevisionUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibrary;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.CommonLeafs;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.module.DeviationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.set.parameters.ModuleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.set.parameters.module.SubmoduleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.DatastoreBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.ModuleSetBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.yang.library.parameters.SchemaBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.util.BindingMap;

@ExtendWith(MockitoExtension.class)
class YangLibraryWriterTest {
    private static final YangLibrarySchemaSourceUrlProvider URL_PROVIDER = (moduleSetName, moduleName, revision) ->
            Optional.of(new Uri("/url/to/" + moduleName + (revision == null ? "" : "/" + revision)));
    private static final InstanceIdentifier<YangLibrary> YANG_LIBRARY_PATH =
        InstanceIdentifier.create(YangLibrary.class);
    private static final InstanceIdentifier<ModulesState> MODULES_STATE_PATH =
        InstanceIdentifier.create(ModulesState.class);
    private static final boolean WITH_LEGACY = true;
    private static final boolean NO_LEGACY = false;
    private static final boolean WITH_URLS = true;
    private static final boolean NO_URLS = false;

    @Mock
    private DOMSchemaService schemaService;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private WriteTransaction writeTransaction;
    @Mock
    private Registration registration;
    @Mock
    private YangLibraryWriter.Configuration config;
    @Captor
    private ArgumentCaptor<YangLibrary> yangLibraryCaptor;
    @Captor
    private ArgumentCaptor<ModulesState> modulesStateCaptor;
    private YangLibraryWriter writer;

    @BeforeEach
    void beforeEach() {
        doReturn(registration).when(schemaService).registerSchemaContextListener(any());
    }

    private YangLibraryWriter.Configuration setupConfig(final boolean writeLegacy) {
        doReturn(writeLegacy).when(config).write$_$legacy();
        return config;
    }

    @Test
    @DisplayName("No update bc context has no ietf-yang-library")
    void noUpdate() {
        writer = new YangLibraryWriter(schemaService, dataBroker, setupConfig(NO_LEGACY));
        writer.onModelContextUpdated(parseYangResources(YangLibraryWriterTest.class,
            "/test-module.yang", "/test-submodule.yang"));
        verifyNoInteractions(dataBroker);
    }

    @ParameterizedTest(name = "Write data -- with URLs: {0}, include legacy: {1}")
    @MethodSource("writeContentArgs")
    void writeContent(final boolean withUrls, final boolean writeLegacy, final YangLibrary expectedData,
            final ModulesState expectedLegacyData) {
        doReturn(writeTransaction).when(dataBroker).newWriteOnlyTransaction();
        doReturn(emptyFluentFuture()).when(writeTransaction).commit();

        writer = new YangLibraryWriter(schemaService, dataBroker, setupConfig(writeLegacy));
        if (withUrls) {
            writer.schemaSourceUrlProvider = URL_PROVIDER;
        }
        writer.onModelContextUpdated(parseYangResources(YangLibraryWriterTest.class,
            "/test-module.yang", "/test-submodule.yang", "/test-more.yang", "/ietf-yang-library@2019-01-04.yang",
            "/ietf-datastores@2018-02-14.yang", "/ietf-yang-types.yang", "/ietf-inet-types.yang"));

        verify(writeTransaction).put(eq(OPERATIONAL), eq(YANG_LIBRARY_PATH), yangLibraryCaptor.capture());
        assertEquals(expectedData, yangLibraryCaptor.getValue());
        if (writeLegacy) {
            verify(writeTransaction).put(eq(OPERATIONAL), eq(MODULES_STATE_PATH), modulesStateCaptor.capture());
            assertEquals(expectedLegacyData, modulesStateCaptor.getValue());
        } else {
            verify(writeTransaction, never()).put(eq(OPERATIONAL), eq(MODULES_STATE_PATH), any());
        }
        verify(writeTransaction).commit();
    }

    private static Stream<Arguments> writeContentArgs() {
        return Stream.of(
            Arguments.of(NO_URLS, NO_LEGACY, buildYangLibrary(NO_URLS), null),
            Arguments.of(NO_URLS, WITH_LEGACY, buildYangLibrary(NO_URLS), buildModulesState(NO_URLS)),
            Arguments.of(WITH_URLS, NO_LEGACY, buildYangLibrary(WITH_URLS), null),
            Arguments.of(WITH_URLS, WITH_LEGACY, buildYangLibrary(WITH_URLS), buildModulesState(WITH_URLS)));
    }

    @ParameterizedTest(name = "Clear data on close -- include legacy: {0}")
    @ValueSource(booleans = {false, true})
    void clearOnClose(final boolean writeLegacy) throws Exception {
        doReturn(writeTransaction).when(dataBroker).newWriteOnlyTransaction();
        doReturn(emptyFluentFuture()).when(writeTransaction).commit();

        new YangLibraryWriter(schemaService, dataBroker, setupConfig(writeLegacy)).close();
        verify(writeTransaction).delete(OPERATIONAL, YANG_LIBRARY_PATH);
        if (writeLegacy) {
            verify(writeTransaction).delete(OPERATIONAL, MODULES_STATE_PATH);
        } else {
            verify(writeTransaction, never()).delete(OPERATIONAL, MODULES_STATE_PATH);
        }
        verify(writeTransaction).commit();
    }

    private static YangLibrary buildYangLibrary(final boolean withUrls) {
        return new YangLibraryBuilder()
            .setModuleSet(BindingMap.of(
                new ModuleSetBuilder()
                    .setName(DEFAULT_MODULE_SET_NAME)
                    .setModule(BindingMap.of(
                        new ModuleBuilder().setName(new YangIdentifier("test-module_2013-07-22"))
                            .setNamespace(new Uri("test:namespace"))
                            .setRevision(new RevisionIdentifier("2013-07-22"))
                            .setLocation(withUrls ? Set.of(new Uri("/url/to/test-module/2013-07-22")) : null)
                            .setSubmodule(BindingMap.of(
                                new SubmoduleBuilder()
                                    .setName(new YangIdentifier("test-submodule"))
                                    .setRevision(RevisionUtils.emptyRevision().getRevisionIdentifier())
                                    .setLocation(withUrls ? Set.of(new Uri("/url/to/test-submodule")) : null)
                                    .build()))
                            .setDeviation(Set.of(new YangIdentifier("test-more_2023-07-25")))
                            .build(),
                        new ModuleBuilder().setName(new YangIdentifier("test-more_2023-07-25"))
                            .setNamespace(new Uri("test:more"))
                            .setRevision(new RevisionIdentifier("2023-07-25"))
                            .setLocation(withUrls ? Set.of(new Uri("/url/to/test-more/2023-07-25")) : null)
                            .setFeature(Set.of(
                                new YangIdentifier("first-feature"), new YangIdentifier("second-feature")))
                            .build(),
                        new ModuleBuilder().setName(new YangIdentifier("ietf-yang-library_2019-01-04"))
                            .setNamespace(new Uri("urn:ietf:params:xml:ns:yang:ietf-yang-library"))
                            .setRevision(new RevisionIdentifier("2019-01-04"))
                            .setLocation(withUrls ? Set.of(new Uri("/url/to/ietf-yang-library/2019-01-04")) : null)
                            .build(),
                        new ModuleBuilder().setName(new YangIdentifier("ietf-datastores_2018-02-14"))
                            .setNamespace(new Uri("urn:ietf:params:xml:ns:yang:ietf-datastores"))
                            .setRevision(new RevisionIdentifier("2018-02-14"))
                            .setLocation(withUrls ? Set.of(new Uri("/url/to/ietf-datastores/2018-02-14")) : null)
                            .build(),
                        new ModuleBuilder().setName(new YangIdentifier("ietf-inet-types_2010-09-24"))
                            .setNamespace(new Uri("urn:ietf:params:xml:ns:yang:ietf-inet-types"))
                            .setRevision(new RevisionIdentifier("2010-09-24"))
                            .setLocation(withUrls ? Set.of(new Uri("/url/to/ietf-inet-types/2010-09-24")) : null)
                            .build(),
                        new ModuleBuilder().setName(new YangIdentifier("ietf-yang-types_2010-09-24"))
                            .setNamespace(new Uri("urn:ietf:params:xml:ns:yang:ietf-yang-types"))
                            .setRevision(new RevisionIdentifier("2010-09-24"))
                            .setLocation(withUrls ? Set.of(new Uri("/url/to/ietf-yang-types/2010-09-24")) : null)
                            .build()))
                    .build()))
            .setSchema(BindingMap.of(new SchemaBuilder()
                .setName(DEFAULT_SCHEMA_NAME)
                .setModuleSet(Set.of(DEFAULT_MODULE_SET_NAME))
                .build()))
            .setDatastore(BindingMap.of(
                new DatastoreBuilder().setName(Operational.VALUE)
                    .setSchema(DEFAULT_SCHEMA_NAME)
                    .build()))
            .setContentId("1")
            .build();
    }

    private static ModulesState buildModulesState(final boolean withUrls) {
        return new ModulesStateBuilder()
            .setModule(BindingMap.of(
                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104
                    .module.list.ModuleBuilder()
                    .setName(new YangIdentifier("test-module_2013-07-22"))
                    .setNamespace(new Uri("test:namespace"))
                    .setRevision(new CommonLeafs.Revision(new RevisionIdentifier("2013-07-22")))
                    .setSchema(withUrls ? new Uri("/url/to/test-module/2013-07-22") : null)
                    .setSubmodule(BindingMap.of(
                        new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104
                            .module.list.module.SubmoduleBuilder()
                            .setName(new YangIdentifier("test-submodule"))
                            .setRevision(LegacyRevisionUtils.emptyRevision())
                            .setSchema(withUrls ? new Uri("/url/to/test-submodule") : null)
                            .build()))
                    .setDeviation(BindingMap.of(
                        new DeviationBuilder()
                            .setName(new YangIdentifier("test-more_2023-07-25"))
                            .setRevision(new CommonLeafs.Revision(new RevisionIdentifier("2023-07-25")))
                            .build()
                    ))
                    .setConformanceType(Import)
                    .build(),
                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104
                    .module.list.ModuleBuilder()
                    .setName(new YangIdentifier("test-more_2023-07-25"))
                    .setNamespace(new Uri("test:more"))
                    .setRevision(new CommonLeafs.Revision(new RevisionIdentifier("2023-07-25")))
                    .setSchema(withUrls ? new Uri("/url/to/test-more/2023-07-25") : null)
                    .setFeature(Set.of(
                        new YangIdentifier("first-feature"), new YangIdentifier("second-feature")))
                    .setConformanceType(Implement)
                    .build(),
                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104
                    .module.list.ModuleBuilder()
                    .setName(new YangIdentifier("ietf-yang-library_2019-01-04"))
                    .setNamespace(new Uri("urn:ietf:params:xml:ns:yang:ietf-yang-library"))
                    .setRevision(new CommonLeafs.Revision(new RevisionIdentifier("2019-01-04")))
                    .setSchema(withUrls ? new Uri("/url/to/ietf-yang-library/2019-01-04") : null)
                    .setConformanceType(Import)
                    .build(),
                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104
                    .module.list.ModuleBuilder()
                    .setName(new YangIdentifier("ietf-datastores_2018-02-14"))
                    .setNamespace(new Uri("urn:ietf:params:xml:ns:yang:ietf-datastores"))
                    .setRevision(new CommonLeafs.Revision(new RevisionIdentifier("2018-02-14")))
                    .setSchema(withUrls ? new Uri("/url/to/ietf-datastores/2018-02-14") : null)
                    .setConformanceType(Import)
                    .build(),
                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104
                    .module.list.ModuleBuilder()
                    .setName(new YangIdentifier("ietf-inet-types_2010-09-24"))
                    .setNamespace(new Uri("urn:ietf:params:xml:ns:yang:ietf-inet-types"))
                    .setRevision(new CommonLeafs.Revision(new RevisionIdentifier("2010-09-24")))
                    .setSchema(withUrls ? new Uri("/url/to/ietf-inet-types/2010-09-24") : null)
                    .setConformanceType(Import)
                    .build(),
                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104
                    .module.list.ModuleBuilder()
                    .setName(new YangIdentifier("ietf-yang-types_2010-09-24"))
                    .setNamespace(new Uri("urn:ietf:params:xml:ns:yang:ietf-yang-types"))
                    .setRevision(new CommonLeafs.Revision(new RevisionIdentifier("2010-09-24")))
                    .setSchema(withUrls ? new Uri("/url/to/ietf-yang-types/2010-09-24") : null)
                    .setConformanceType(Import)
                    .build()))
            .setModuleSetId("1")
            .build();
    }
}
