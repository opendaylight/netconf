/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import com.google.gson.JsonIOException;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ConsumableBody;

/**
 * An abstract request body backed by an {@link InputStream}. In controls the access to input stream, so that it can
 * only be taken once.
 */
@NonNullByDefault
abstract sealed class RequestBody extends ConsumableBody
        permits ChildBody, DataPostBody, OperationInputBody, PatchBody, ResourceBody {
    RequestBody(final InputStream inputStream) {
        super(inputStream);
    }

    static final Exception unmaskIOException(final Exception ex) {
        return ex instanceof JsonIOException jsonIO && jsonIO.getCause() instanceof IOException io ? io : ex;
    }
}
