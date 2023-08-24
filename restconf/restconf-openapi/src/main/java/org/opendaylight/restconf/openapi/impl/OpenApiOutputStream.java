/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNull;

public class OpenApiOutputStream extends OutputStream {
    private final OpenApiInputStream inputStream;

    public OpenApiOutputStream(final @NonNull OpenApiInputStream inputStream) {
        this.inputStream = Objects.requireNonNull(inputStream);
    }

    @Override
    public void write(int i) throws IOException {
        // JsonGenerator writes into OutputStream but response needs to be created from InputStream
        // perhaps use java.io.PipedOutputStream?
    }
}
