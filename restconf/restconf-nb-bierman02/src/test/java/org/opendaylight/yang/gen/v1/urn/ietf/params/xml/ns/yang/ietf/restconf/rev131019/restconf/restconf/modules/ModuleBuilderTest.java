/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev131019.restconf.restconf.modules;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev131019.RevisionIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;

public class ModuleBuilderTest {

    @Test
    public void testModuleBuilder() {
        final ModuleBuilder moduleBuilder = new ModuleBuilder();
        final Module.Revision revision = new Module.Revision(new RevisionIdentifier("2016-10-11"));
        final YangIdentifier yangIdentifierOne = new YangIdentifier("YangIdentifier1");
        final YangIdentifier yangIdentifierTwo = new YangIdentifier("YangIdentifier2");
        final Uri namespace = new Uri("namespace");
        final List<YangIdentifier> yangIdentifierList = ImmutableList.of(yangIdentifierOne, yangIdentifierTwo);
        final ModuleKey moduleKeyOne = new ModuleKey(yangIdentifierOne, revision);
        final ModuleKey moduleKeyTwo = new ModuleKey(moduleKeyOne);
        moduleBuilder.setRevision(revision);
        moduleBuilder.setDeviation(yangIdentifierList);
        moduleBuilder.setFeature(yangIdentifierList);
        moduleBuilder.setName(yangIdentifierOne);
        moduleBuilder.setNamespace(namespace);
        moduleBuilder.withKey(moduleKeyOne);
        final Module moduleOne = moduleBuilder.build();
        final Module moduleTwo = new ModuleBuilder(moduleOne).build();

        assertNotNull(moduleBuilder);
        assertNotNull(revision);
        assertNotNull(yangIdentifierOne);
        assertNotNull(yangIdentifierTwo);
        assertNotNull(namespace);
        assertNotNull(yangIdentifierList);
        assertNotNull(moduleKeyOne);
        assertNotNull(moduleKeyOne.hashCode());
        assertNotNull(moduleKeyOne.toString());
        assertNotNull(moduleBuilder.toString());
        assertNotNull(moduleBuilder.hashCode());

        assertEquals(moduleKeyOne, moduleKeyTwo);
        assertEquals(revision, moduleKeyOne.getRevision());
        assertEquals(yangIdentifierOne, moduleKeyOne.getName());
        assertEquals(revision, moduleBuilder.getRevision());
        assertEquals(yangIdentifierList, moduleBuilder.getDeviation());
        assertEquals(yangIdentifierList, moduleBuilder.getFeature());
        assertEquals(yangIdentifierOne, moduleBuilder.getName());
        assertEquals(namespace, moduleBuilder.getNamespace());
        assertEquals(moduleKeyOne, moduleBuilder.key());
        assertEquals(moduleOne.toString(), moduleTwo.toString());
        assertEquals(moduleKeyOne.toString(), moduleKeyTwo.toString());

        assertTrue(moduleOne.equals(moduleTwo));
        assertTrue(moduleKeyOne.equals(moduleKeyTwo));
    }
}
