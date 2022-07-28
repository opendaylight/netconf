/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.legacy;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.nb.rfc8040.DepthParam;
import org.opendaylight.restconf.nb.rfc8040.PrettyPrintParam;
import org.opendaylight.restconf.nb.rfc8040.ReadDataParams;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * This holds various options acquired from a requests's query part. This class needs to be further split up to make
 * sense of it, as parts of it pertain to how a {@link NormalizedNodePayload} should be created while others how it
 * needs to be processed (for example filtered).
 */
@Beta
// FIXME: this probably needs to be renamed back to WriterParams, or somesuch
public final class QueryParameters {
    private static final @NonNull QueryParameters EMPTY = of(ReadDataParams.empty());

    private final @NonNull ReadDataParams params;
    private final List<YangInstanceIdentifier> fieldPaths;
    private final List<Set<QName>> fields;

    private QueryParameters(final ReadDataParams params, final List<Set<QName>> fields,
            final List<YangInstanceIdentifier> fieldPaths) {
        this.params = requireNonNull(params);
        this.fields = fields;
        this.fieldPaths = fieldPaths;
    }

    public static @NonNull QueryParameters empty() {
        return EMPTY;
    }

    public static @NonNull QueryParameters of(final ReadDataParams params) {
        return new QueryParameters(params, null, null);
    }

    public static @NonNull QueryParameters ofFields(final ReadDataParams params, final List<Set<QName>> fields) {
        return new QueryParameters(params, fields, null);
    }

    public static @NonNull QueryParameters ofFieldPaths(final ReadDataParams params,
            final List<YangInstanceIdentifier> fieldPaths) {
        return new QueryParameters(params, null, fieldPaths);
    }

    public @NonNull ReadDataParams params() {
        return params;
    }

    public @Nullable DepthParam depth() {
        return params.depth();
    }

    public @Nullable PrettyPrintParam prettyPrint() {
        return params.prettyPrint();
    }

    public @Nullable List<Set<QName>> fields() {
        return fields;
    }

    public @Nullable List<YangInstanceIdentifier> fieldPaths() {
        return fieldPaths;
    }
}
