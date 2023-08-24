/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import java.io.IOException;
import java.io.InputStream;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;

public final class SecuritySchemesStream extends InputStream {
    public SecuritySchemesStream(final OpenApiBodyWriter writer) {
    }

    @Override
    public int read() throws IOException {
        return -1;
    }

    @Override
    public int read(final byte[] array, final int off, final int len) throws IOException {
        return super.read(array, off, len);
    }
}
