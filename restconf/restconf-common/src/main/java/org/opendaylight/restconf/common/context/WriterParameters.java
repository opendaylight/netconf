/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.context;

import java.util.List;
import java.util.Set;
import org.opendaylight.yangtools.yang.common.QName;

public final class WriterParameters {
    private final String content;
    private final Integer depth;
    private final List<Set<QName>> fields;
    private final boolean prettyPrint;
    private final boolean tagged;
    private final String withDefault;
    private final List<String> parentChildRelation;

    private WriterParameters(final WriterParametersBuilder builder) {
        this.content = builder.content;
        this.depth = builder.depth;
        this.fields = builder.fields;
        this.prettyPrint = builder.prettyPrint;
        this.tagged = builder.tagged;
        this.withDefault = builder.withDefault;
        this.parentChildRelation = builder.parentChildRelation;
    }

    public String getContent() {
        return this.content;
    }

    public Integer getDepth() {
        return this.depth;
    }

    public List<Set<QName>> getFields() {
        return this.fields;
    }

    public boolean isPrettyPrint() {
        return this.prettyPrint;
    }

    public boolean isTagged() {
        return this.tagged;
    }

    public String getWithDefault() {
        return withDefault;
    }

    public List<String> getParentChildRelation() {
        return parentChildRelation;
    }

    public static class WriterParametersBuilder {
        private String content;
        private Integer depth;
        private List<Set<QName>> fields;
        private boolean prettyPrint;
        private boolean tagged;
        private String withDefault;
        private List<String> parentChildRelation;

        public WriterParametersBuilder() {

        }

        public WriterParametersBuilder setContent(final String content) {
            this.content = content;
            return this;
        }

        public WriterParametersBuilder setDepth(final int depth) {
            this.depth = depth;
            return this;
        }

        public WriterParametersBuilder setFields(final List<Set<QName>> fields) {
            this.fields = fields;
            return this;
        }

        public WriterParametersBuilder setPrettyPrint(final boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        public WriterParametersBuilder setWithDefault(final String withDefault) {
            this.withDefault = withDefault;
            return this;
        }

        public WriterParametersBuilder setParentChildRelation(final List<String> parentChildRelation) {
            this.parentChildRelation = parentChildRelation;
            return this;
        }

        public WriterParameters build() {
            return new WriterParameters(this);
        }

        public void setTagged(final boolean tagged) {
            this.tagged = tagged;
        }
    }
}
