/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.api.query.RestconfQueryParam;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * The set of {@link RestconfQueryParam}s governing output formatting.
 *
 * @param prettyPrint the {@link PrettyPrintParam} parameter
 */
@NonNullByDefault
public record FormatParameters(PrettyPrintParam prettyPrint) implements Immutable {
    public static final FormatParameters COMPACT = new FormatParameters(PrettyPrintParam.FALSE);
    public static final FormatParameters PRETTY = new FormatParameters(PrettyPrintParam.TRUE);

    /**
     * Return the {@link PrettyPrintParam} parameter.
     */
    public FormatParameters {
        requireNonNull(prettyPrint);
    }
}
