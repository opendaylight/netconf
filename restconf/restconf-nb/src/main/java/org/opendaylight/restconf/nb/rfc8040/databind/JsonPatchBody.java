/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2023 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import java.io.IOException;
import java.io.InputStream;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.patch.PatchContext;

public final class JsonPatchBody extends PatchBody {
    public JsonPatchBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    PatchContext toPatchContext(final InstanceIdentifierContext targetResource, final InputStream inputStream)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }
}