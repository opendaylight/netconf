/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import java.util.Collection;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Represents collection of request processors.
 */
interface RequestMapping {

    /**
     * Checks own collection for request processors finding the one suitable to request parameters.
     *
     * @param context request context
     * @return suitable request processor instance or null if contains no such processor
     */
    default @Nullable RequestProcessor findMatching(RequestContext context) {
        return processors().stream()
            .filter(processor -> processor.matches(context)).findFirst().orElse(null);
    }

    /**
     * Returns collection of request processors owned.
     *
     * @return collection of processors.
     */
    @NonNull Collection<RequestProcessor> processors();
}
