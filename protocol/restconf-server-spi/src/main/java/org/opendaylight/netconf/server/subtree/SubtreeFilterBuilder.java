/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.subtree;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.api.DatabindContext;

/**
 * A builder for {@link SubtreeFilter}.
 */
@NonNullByDefault
public final class SubtreeFilterBuilder extends SiblingSetBuilder<SubtreeFilter> {
    SubtreeFilterBuilder(final DatabindContext databind) {
        super(databind);
    }

    @Override
    public SubtreeFilter build() {
        return new SubtreeFilter(this);
    }
}