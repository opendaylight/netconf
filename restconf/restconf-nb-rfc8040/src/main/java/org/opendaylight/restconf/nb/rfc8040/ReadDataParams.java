/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * Parser and holder of query parameters from uriInfo for data and datastore read operations.
 */
@Beta
// FIXME: this should be a record once we have JDK17+
public final class ReadDataParams implements Immutable {
    private static final @NonNull ReadDataParams EMPTY =
        new ReadDataParams(ContentParameter.ALL, null, null, null, false, false);

    private final @NonNull ContentParameter content;
    private final WithDefaultsParameter withDefaults;
    private final FieldsParameter fields;
    private final DepthParameter depth;
    private final boolean prettyPrint;
    private final boolean tagged;

    private ReadDataParams(final ContentParameter content,  final DepthParameter depth, final FieldsParameter fields,
            final WithDefaultsParameter withDefaults, final boolean tagged, final boolean prettyPrint) {
        this.content = requireNonNull(content);
        this.depth = depth;
        this.fields = fields;
        this.withDefaults = withDefaults;
        this.tagged = tagged;
        this.prettyPrint = prettyPrint;
    }

    public static @NonNull ReadDataParams empty() {
        return EMPTY;
    }

    public static @NonNull ReadDataParams of(final ContentParameter content,  final DepthParameter depth,
            final FieldsParameter fields, final WithDefaultsParameter withDefaults, final boolean tagged,
            final boolean prettyPrint) {
        return new ReadDataParams(content, depth, fields, withDefaults, tagged, prettyPrint);
    }

    public @NonNull ContentParameter content() {
        return content;
    }

    public @Nullable DepthParameter depth() {
        return depth;
    }

    public @Nullable FieldsParameter fields() {
        return fields;
    }

    public @Nullable WithDefaultsParameter withDefaults() {
        return withDefaults;
    }

    public boolean prettyPrint() {
        return prettyPrint;
    }

    // FIXME: for migration only
    public boolean tagged() {
        return tagged;
    }

    @Override
    public String toString() {
        final var helper = MoreObjects.toStringHelper(this).add("content", content.uriValue());
        if (depth != null) {
            helper.add("depth", depth.value());
        }
        if (fields != null) {
            helper.add("fields", fields.toString());
        }
        if (withDefaults != null) {
            helper.add("withDefaults", withDefaults.uriValue());
        }
        return helper.add("tagged", tagged).add("prettyPrint", prettyPrint).toString();
    }
}
