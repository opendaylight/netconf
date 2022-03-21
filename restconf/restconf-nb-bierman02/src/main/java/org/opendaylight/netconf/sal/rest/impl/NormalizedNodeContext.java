/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

@Deprecated(forRemoval = true, since = "2.0.6")
// Non-final for mocking
public class NormalizedNodeContext {
    private final InstanceIdentifierContext context;
    private final ImmutableMap<String, Object> headers;
    private final WriterParameters writerParameters;
    private final NormalizedNode data;

    public NormalizedNodeContext(final InstanceIdentifierContext context,
            final NormalizedNode data, final WriterParameters writerParameters,
            final ImmutableMap<String, Object> headers) {
        this.context = context;
        this.data = data;
        this.writerParameters = writerParameters;
        this.headers = requireNonNull(headers);
    }

    public NormalizedNodeContext(final InstanceIdentifierContext context,
                                 final NormalizedNode data, final WriterParameters writerParameters) {
        this(context, data, writerParameters, ImmutableMap.of());
    }

    public NormalizedNodeContext(final InstanceIdentifierContext context,
                                 final NormalizedNode data) {
        this(context, data, WriterParameters.EMPTY, ImmutableMap.of());
    }

    public NormalizedNodeContext(final InstanceIdentifierContext context,
            final NormalizedNode data, final ImmutableMap<String, Object> headers) {
        this(context, data, WriterParameters.EMPTY, headers);
    }

    public InstanceIdentifierContext getInstanceIdentifierContext() {
        return context;
    }

    public NormalizedNode getData() {
        return data;
    }

    public WriterParameters getWriterParameters() {
        return writerParameters;
    }

    /**
     * Return headers of {@code NormalizedNodeContext}.
     *
     * @return map of headers
     */
    public ImmutableMap<String, Object> getNewHeaders() {
        return headers;
    }
}
