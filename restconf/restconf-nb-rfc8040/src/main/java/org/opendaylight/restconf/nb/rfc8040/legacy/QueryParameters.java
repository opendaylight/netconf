/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.legacy;

import java.util.List;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.nb.rfc8040.ContentParameter;
import org.opendaylight.restconf.nb.rfc8040.WithDefaultsParameter;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * This holds various options acquired from a requests's query part. This class needs to be further split up to make
 * sense of it, as parts of it pertain to how a {@link NormalizedNodePayload} should be created while others how it
 * needs to be processed (for example filtered).
 */
public final class QueryParameters {
    public static final class Builder {
        private List<YangInstanceIdentifier> fieldPaths;
        private List<Set<QName>> fields;
        private ContentParameter content;
        private WithDefaultsParameter withDefault;
        // FIXME: this should be a DepthParameter
        private Integer depth;
        private boolean prettyPrint;
        private boolean tagged;

        Builder() {
            // Hidden on purpose
        }

        public Builder setContent(final ContentParameter content) {
            this.content = content;
            return this;
        }

        public Builder setDepth(final int depth) {
            this.depth = depth;
            return this;
        }

        public Builder setFields(final List<Set<QName>> fields) {
            this.fields = fields;
            return this;
        }

        public Builder setFieldPaths(final List<YangInstanceIdentifier> fieldPaths) {
            this.fieldPaths = fieldPaths;
            return this;
        }

        // FIXME: this is not called from anywhere. Create a PrettyPrintParameter or similar to hold it
        public Builder setPrettyPrint(final boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        public Builder setTagged(final boolean tagged) {
            this.tagged = tagged;
            return this;
        }

        public Builder setWithDefault(final WithDefaultsParameter withDefault) {
            this.withDefault = withDefault;
            return this;
        }

        public @NonNull QueryParameters build() {
            return new QueryParameters(this);
        }
    }

    private static final @NonNull QueryParameters EMPTY = new Builder().build();

    private final List<YangInstanceIdentifier> fieldPaths;
    private final List<Set<QName>> fields;
    private final WithDefaultsParameter withDefault;
    private final ContentParameter content;
    private final Integer depth;
    private final boolean prettyPrint;
    private final boolean tagged;

    private QueryParameters(final Builder builder) {
        content = builder.content;
        depth = builder.depth;
        fields = builder.fields;
        fieldPaths = builder.fieldPaths;
        tagged = builder.tagged;
        prettyPrint = builder.prettyPrint;
        withDefault = builder.withDefault;
    }

    public static @NonNull QueryParameters empty() {
        return EMPTY;
    }

    public static @NonNull Builder builder() {
        return new Builder();
    }

    public ContentParameter getContent() {
        return content;
    }

    public Integer getDepth() {
        return depth;
    }

    public List<Set<QName>> getFields() {
        return fields;
    }

    public List<YangInstanceIdentifier> getFieldPaths() {
        return fieldPaths;
    }

    public WithDefaultsParameter getWithDefault() {
        return withDefault;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public boolean isTagged() {
        return tagged;
    }
}
