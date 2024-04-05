/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.api.query.RestconfQueryParam;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * The set of {@link RestconfQueryParam}s governing output formatting.
 */
@NonNullByDefault
public interface FormatParameters extends Immutable {
    /**
     * Return the {@link PrettyPrintParam} parameter.
     *
     * @return the {@link PrettyPrintParam} parameter
     */
    PrettyPrintParam prettyPrint();
}
