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
import org.opendaylight.restconf.common.context.WriterParameters;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * A RFC8040 overlay over {@link WriterParameters}. This holds various options acquired from a requests's query part.
 * This class needs to be further split up to make sense of it, as parts of it pertain to how a
 * {@link NormalizedNodePayload} should be created while others how it needs to be processed (for example filtered).
 */
public final class QueryParameters extends WriterParameters {
    public static final class Builder extends WriterParametersBuilder {
        private List<YangInstanceIdentifier> fieldPaths;
        private List<Set<QName>> fields;
        private String withDefault;
        private boolean tagged;
        private String content;

        Builder() {
            // Hidden on purpose
        }

        public Builder setContent(final String content) {
            this.content = content;
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

        public Builder setTagged(final boolean tagged) {
            this.tagged = tagged;
            return this;
        }

        public Builder setWithDefault(final String withDefault) {
            this.withDefault = withDefault;
            return this;
        }

        @Override
        public @NonNull QueryParameters build() {
            return new QueryParameters(this);
        }
    }

    private static final @NonNull QueryParameters EMPTY = new Builder().build();

    private final List<YangInstanceIdentifier> fieldPaths;
    private final List<Set<QName>> fields;
    private final String withDefault;
    private final boolean tagged;
    private final String content;

    private QueryParameters(final Builder builder) {
        super(builder);
        content = builder.content;
        fields = builder.fields;
        fieldPaths = builder.fieldPaths;
        tagged = builder.tagged;
        withDefault = builder.withDefault;
    }

    public static @NonNull QueryParameters empty() {
        return EMPTY;
    }

    public static @NonNull Builder builder() {
        return new Builder();
    }

    public String getContent() {
        return content;
    }

    public List<Set<QName>> getFields() {
        return fields;
    }

    public List<YangInstanceIdentifier> getFieldPaths() {
        return fieldPaths;
    }

    public String getWithDefault() {
        return withDefault;
    }

    public boolean isTagged() {
        return tagged;
    }
}
