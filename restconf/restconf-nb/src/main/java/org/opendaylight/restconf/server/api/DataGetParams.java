/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * Supported query parameters of {@code /data} {@code GET} HTTP operation, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.3">RFC8040 section 4.3</a>.
 */
public record DataGetParams(
        @NonNull ContentParam content,
        @Nullable DepthParam depth,
        @Nullable FieldsParam fields,
        @Nullable WithDefaultsParam withDefaults,
        @Nullable PrettyPrintParam prettyPrint) implements Immutable {
    private static final @NonNull DataGetParams EMPTY =
        new DataGetParams(ContentParam.ALL, null, null, null, null);

    public DataGetParams {
        requireNonNull(content);
    }

    public static @NonNull DataGetParams empty() {
        return EMPTY;
    }
}
