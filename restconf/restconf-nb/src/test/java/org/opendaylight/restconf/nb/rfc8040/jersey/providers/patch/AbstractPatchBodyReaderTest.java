/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch;

import static org.junit.Assert.assertNotNull;

import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.test.AbstractBodyReaderTest;

abstract class AbstractPatchBodyReaderTest extends AbstractBodyReaderTest {

    static final void checkPatchContext(final PatchContext patchContext) {
        assertNotNull(patchContext.getData());

        final var iid = patchContext.getInstanceIdentifierContext();
        assertNotNull(iid);

        assertNotNull(iid.getInstanceIdentifier());
        assertNotNull(iid.getSchemaContext());
        assertNotNull(iid.getSchemaNode());
    }

    static final void checkPatchContextMountPoint(final PatchContext patchContext) {
        checkPatchContext(patchContext);
        assertNotNull(patchContext.getInstanceIdentifierContext().getMountPoint());
    }
}
