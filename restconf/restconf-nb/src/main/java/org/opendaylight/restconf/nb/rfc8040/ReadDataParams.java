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
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * Parser and holder of query parameters from uriInfo for data and datastore read operations.
 */
@Beta
// FIXME: this should be a record once we have JDK17+
public final class ReadDataParams implements Immutable {
    private static final @NonNull ReadDataParams EMPTY =
        new ReadDataParams(ContentParam.ALL, null, null, null, null);

    private final @NonNull ContentParam content;
    private final WithDefaultsParam withDefaults;
    private final PrettyPrintParam prettyPrint;
    private final FieldsParam fields;
    private final DepthParam depth;

    private ReadDataParams(final ContentParam content,  final DepthParam depth, final FieldsParam fields,
            final WithDefaultsParam withDefaults, final PrettyPrintParam prettyPrint) {
        this.content = requireNonNull(content);
        this.depth = depth;
        this.fields = fields;
        this.withDefaults = withDefaults;
        this.prettyPrint = prettyPrint;
    }

    public static @NonNull ReadDataParams empty() {
        return EMPTY;
    }

    public static @NonNull ReadDataParams of(final ContentParam content,  final DepthParam depth,
            final FieldsParam fields, final WithDefaultsParam withDefaults, final PrettyPrintParam prettyPrint) {
        return new ReadDataParams(content, depth, fields, withDefaults, prettyPrint);
    }

    public @NonNull ContentParam content() {
        return content;
    }

    public @Nullable DepthParam depth() {
        return depth;
    }

    public @Nullable FieldsParam fields() {
        return fields;
    }

    public @Nullable WithDefaultsParam withDefaults() {
        return withDefaults;
    }

    public @Nullable PrettyPrintParam prettyPrint() {
        return prettyPrint;
    }

    @Override
    public String toString() {
        final var helper = MoreObjects.toStringHelper(this).add("content", content.paramValue());
        if (depth != null) {
            helper.add("depth", depth.value());
        }
        if (fields != null) {
            helper.add("fields", fields.toString());
        }
        if (withDefaults != null) {
            helper.add("withDefaults", withDefaults.paramValue());
        }
        if (prettyPrint != null) {
            helper.add("prettyPrint", prettyPrint.value());
        }
        return helper.toString();
    }
}
