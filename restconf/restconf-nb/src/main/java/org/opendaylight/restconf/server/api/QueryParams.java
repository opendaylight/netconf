/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Map.Entry;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;

/**
 * A {@link QueryParameters} implementation which is congnizant of a default {@link PrettyPrintParam}.
 */
@NonNullByDefault
public record QueryParams(QueryParameters delegate, PrettyPrintParam prettyPrint) implements QueryParameters {
    public QueryParams {
        requireNonNull(delegate);
        requireNonNull(prettyPrint);
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public Collection<? extends Entry<String, String>> asCollection() {
        return delegate.asCollection();
    }

    @Override
    public @Nullable String lookup(final String paramName) {
        final var ret = delegate.lookup(paramName);
        if (ret != null) {
            return ret;
        }
        return PrettyPrintParam.uriName.equals(paramName) ? prettyPrint.paramValue() : null;
    }
}
