/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev131019.restconf.restconf.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev131019.restconf.restconf.modules.Module.Revision;

public class ModuleRevisionBuilderTest {

    @Test
    public void testModuleRevisionBuilder() {
        final ModuleRevisionBuilder moduleRevisionBuilder = new ModuleRevisionBuilder();
        assertNotNull(moduleRevisionBuilder);
        final Revision revision = ModuleRevisionBuilder.getDefaultInstance("");
        assertNotNull(revision);
        assertEquals("", revision.getString());
        assertEquals(null, revision.getRevisionIdentifier());
    }
}