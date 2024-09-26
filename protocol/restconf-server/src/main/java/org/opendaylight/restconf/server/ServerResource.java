/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Server-side view of a <a href="https://www.rfc-editor.org/rfc/rfc9110#section-3.1>HTTP resource</a>, with opinions
 * about resource addressing and routing. The hierarchical nature of HTTP resources lends itself to step-by-step
 * resolution at each path segment -- like done via {@link SegmentPeeler}.
 *
 * <p>
 * This interface exposes {@link #segment()}, which returns a unencoded string, which can be matched against a String
 * returned by {@link SegmentPeeler#next()}.
 */
@NonNullByDefault
sealed interface ServerResource permits RestconfRequestDispatcher, WellKnownResources {
    /**
     * Returns the value of URI path segment this resource corresponds to.
     *
     * @return the value of URI path segment this resource corresponds to
     */
    String segment();
}
