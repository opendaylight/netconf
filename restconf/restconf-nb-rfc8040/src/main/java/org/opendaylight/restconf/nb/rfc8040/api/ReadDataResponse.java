/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.api;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;

/**
 * Interface encapsulating a response to a {@link ReadDataService} service call.
 */
@Beta
public abstract class ReadDataResponse {

    public static final class OfNormalizedNode extends ReadDataResponse {
        @VisibleForTesting
        public final @NonNull NormalizedNodePayload payload;

        public OfNormalizedNode(final NormalizedNodePayload payload) {
            this.payload = requireNonNull(payload);
        }

        @Override
        public void writeTo(final OutputStream os) throws IOException {
            // TODO Auto-generated method stub
        }
    }

    public abstract void writeTo(OutputStream os) throws IOException;
}
