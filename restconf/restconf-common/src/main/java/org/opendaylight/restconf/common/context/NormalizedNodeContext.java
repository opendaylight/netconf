/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.context;

import java.util.HashMap;
import java.util.Map;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class NormalizedNodeContext {

    private final InstanceIdentifierContext<? extends SchemaNode> context;
    private final NormalizedNode<?,?> data;
    private final WriterParameters writerParameters;
    private Map<String, Object> headers = new HashMap<>();

    public NormalizedNodeContext(final InstanceIdentifierContext<? extends SchemaNode> context,
                                 final NormalizedNode<?, ?> data, final WriterParameters writerParameters) {
        this.context = context;
        this.data = data;
        this.writerParameters = writerParameters;
    }

    public NormalizedNodeContext(final InstanceIdentifierContext<? extends SchemaNode> context,
            final NormalizedNode<?, ?> data, final WriterParameters writerParameters,
            final Map<String, Object> headers) {
        this.context = context;
        this.data = data;
        this.writerParameters = writerParameters;
        this.headers = headers;
    }

    public NormalizedNodeContext(final InstanceIdentifierContext<? extends SchemaNode> context,
                                 final NormalizedNode<?, ?> data) {
        this.context = context;
        this.data = data;
        // default writer parameters
        this.writerParameters = new WriterParameters.WriterParametersBuilder().build();
    }

    public NormalizedNodeContext(final InstanceIdentifierContext<? extends SchemaNode> context,
            final NormalizedNode<?, ?> data, final Map<String, Object> headers) {
        this.context = context;
        this.data = data;
        // default writer parameters
        this.writerParameters = new WriterParameters.WriterParametersBuilder().build();
        this.headers = headers;
    }


    public InstanceIdentifierContext<? extends SchemaNode> getInstanceIdentifierContext() {
        return this.context;
    }

    public NormalizedNode<?, ?> getData() {
        return this.data;
    }

    public WriterParameters getWriterParameters() {
        return this.writerParameters;
    }

    /**
     * Return headers of {@code NormalizedNodeContext}.
     *
     * @return map of headers
     */
    public Map<String, Object> getNewHeaders() {
        return this.headers;
    }
}
