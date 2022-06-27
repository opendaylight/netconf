/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.yang.library;

import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.LegacyRevisionUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesStateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.RevisionIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.CommonLeafs.Revision;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.ModuleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.module.SubmoduleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.util.BindingMap;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ModulesStateTest extends AbstractYangLibraryWriterTest {
    @Test
    public void testUpdate() {
        assertOperationalUpdate(InstanceIdentifier.create(ModulesState.class), new ModulesStateBuilder()
            .setModuleSetId("0")
            .setModule(BindingMap.of(new ModuleBuilder()
                .setName(new YangIdentifier("test-module"))
                .setNamespace(new Uri("test:namespace"))
                .setRevision(new Revision(new RevisionIdentifier("2013-07-22")))
                .setSubmodule(BindingMap.of(new SubmoduleBuilder()
                    .setName(new YangIdentifier("test-submodule"))
                    .setRevision(LegacyRevisionUtils.emptyRevision())
                    .build()))
                .setConformanceType(Module.ConformanceType.Implement)
                .setFeature(Set.of())
                .build(), new ModuleBuilder()
                .setName(new YangIdentifier("ietf-yang-library"))
                .setNamespace(new Uri("urn:ietf:params:xml:ns:yang:ietf-yang-library"))
                .setRevision(new Revision(new RevisionIdentifier("2019-01-04")))
                .setConformanceType(Module.ConformanceType.Implement)
                .setFeature(Set.of())
                .build()))
            .build());
    }
}
