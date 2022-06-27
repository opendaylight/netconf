/*
 * Copyright (c) 2020 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.yang.library;

import com.google.common.collect.ImmutableMap;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.datastores.rev180214.Operational;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.RevisionIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.RevisionUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibrary;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryBuilder;
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
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.util.BindingMap;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class YangLibraryTest extends AbstractYangLibraryWriterTest {
    @Test
    public void testUpdate() {
        assertOperationalUpdate(InstanceIdentifier.create(YangLibrary.class), createTestModuleSet());
    }

    private static YangLibrary createTestModuleSet() {
        Submodule sub = new SubmoduleBuilder()
                .setName(new YangIdentifier("test-submodule"))
                .setRevision(RevisionUtils.emptyRevision().getRevisionIdentifier())
                .build();

        Module modules = new ModuleBuilder().setName(new YangIdentifier("test-module_2013-07-22"))
                .setNamespace(new Uri("test:namespace"))
                .setRevision(new RevisionIdentifier("2013-07-22"))
                .setSubmodule(ImmutableMap.of(sub.key(), sub))
                .setFeature(Set.of())
                .build();

        Module yangLibrary = new ModuleBuilder().setName(new YangIdentifier("ietf-yang-library_2019-01-04"))
                .setNamespace(new Uri("urn:ietf:params:xml:ns:yang:ietf-yang-library"))
                .setRevision(new RevisionIdentifier("2019-01-04"))
                .setFeature(Set.of())
                .build();

        ModuleSet modulesSet = new ModuleSetBuilder()
                .setName("state-modules")
                .setModule(ImmutableMap.of(modules.key(), modules, yangLibrary.key(), yangLibrary))
                .build();


        Schema schema = new SchemaBuilder().setName("state-schema")
                .setModuleSet(Set.of(modulesSet.getName()))
                .build();

        Datastore datastore = new DatastoreBuilder().setName(Operational.VALUE)
                .setSchema(schema.getName())
                .build();

        return new YangLibraryBuilder()
            .setModuleSet(BindingMap.of(modulesSet))
            .setSchema(BindingMap.of(schema))
            .setDatastore(BindingMap.of(datastore))
            .setContentId("0")
            .build();
    }
}