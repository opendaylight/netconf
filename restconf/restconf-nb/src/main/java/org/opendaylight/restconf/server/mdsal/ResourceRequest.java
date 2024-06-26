/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.DefaultRestconfStrategy;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * A request to a resource identified by the URL path.
 */
record ResourceRequest(
        @NonNull DefaultRestconfStrategy strategy,
        @NonNull YangInstanceIdentifier path,
        @NonNull NormalizedNode data) {
    ResourceRequest {
        requireNonNull(strategy);
        requireNonNull(path);
        requireNonNull(data);
    }
}
