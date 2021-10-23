/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.api;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.nb.rfc8040.ContentParameter;
import org.opendaylight.restconf.nb.rfc8040.DepthParameter;
import org.opendaylight.restconf.nb.rfc8040.FieldsParameter;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * RESTCONF Query parameters related to read data operations.
 */
// FIXME: JDK17+: this should be a record
public final class ReadDataParameters implements Immutable {
    private final @NonNull ContentParameter content;
    private final FieldsParameter fields;
    private final DepthParameter depth;

    ReadDataParameters(final ContentParameter content, final DepthParameter depth, final FieldsParameter fields) {
        this.content = requireNonNull(content);
        this.depth = depth;
        this.fields = fields;
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

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).omitNullValues()
            .add("content", content)
            .add("depth", depth)
            .add("fields", fields)
            .toString();
    }
}
