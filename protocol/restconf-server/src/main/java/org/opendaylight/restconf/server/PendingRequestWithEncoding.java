/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A {@link PendingRequest} with an attached {@link MessageEncoding}.
 *
 * @param <T> server response type
 */
@NonNullByDefault
abstract class PendingRequestWithEncoding<T> extends PendingRequest<T> {
    final MessageEncoding encoding;

    PendingRequestWithEncoding(final EndpointInvariants invariants, final MessageEncoding encoding) {
        super(invariants);
        this.encoding = requireNonNull(encoding);
    }
}
