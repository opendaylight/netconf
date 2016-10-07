/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.restconf.impl;

import com.google.common.base.Optional;
import java.util.List;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class WriterParameters {
    private final String content;
    private final Optional<Integer> depth;
    private final List<YangInstanceIdentifier.PathArgument> fields;
    private final boolean prettyPrint;

    private WriterParameters(final WriterParametersBuilder builder) {
        this.content = builder.content;
        this.depth = builder.depth;
        this.fields = builder.fields;
        this.prettyPrint = builder.prettyPrint;
    }

    public String getContent() {
        return content;
    }

    public Optional<Integer> getDepth() {
        return depth;
    }

    public List<YangInstanceIdentifier.PathArgument> getFields() {
        return fields;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public static class WriterParametersBuilder {
        private String content;
        private Optional<Integer> depth = Optional.absent();
        private List<YangInstanceIdentifier.PathArgument> fields;
        private boolean prettyPrint;

        public WriterParametersBuilder() {}

        public WriterParametersBuilder setContent(final String content) {
            this.content = content;
            return this;
        }

        public WriterParametersBuilder setDepth(final int depth) {
            this.depth = Optional.of(depth);
            return this;
        }

        public WriterParametersBuilder setFields(final List<YangInstanceIdentifier.PathArgument> fields) {
            this.fields = fields;
            return this;
        }

        public WriterParametersBuilder setPrettyPrint(final boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        public WriterParameters build() {
            return new WriterParameters(this);
        }
    }
}
