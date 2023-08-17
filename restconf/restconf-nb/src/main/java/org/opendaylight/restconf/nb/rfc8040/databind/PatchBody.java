/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.patch.PatchContext;

/**
 *
 */
public abstract class PatchBody {
    private final InputStream inputStream;

    PatchBody(final InputStream inputStream) {
        this.inputStream = requireNonNull(inputStream);
    }

    public final @NonNull PatchContext toPatchContext() throws IOException {

    }
}
