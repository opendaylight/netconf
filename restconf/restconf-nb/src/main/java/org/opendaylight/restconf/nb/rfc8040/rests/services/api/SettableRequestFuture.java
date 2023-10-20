/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.api;

import static java.util.Objects.requireNonNull;

import org.opendaylight.restconf.api.RestconfResponse;

/**
 * A {@link RequestFuture} which allows the result to be set via {@link #set(RestconfResponse)}.
 */
public final class SettableRequestFuture extends RequestFuture {
    @Override
    public boolean set(final RestconfResponse value) {
        return super.set(requireNonNull(value));
    }
}
