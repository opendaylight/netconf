/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.ParameterAwareNormalizedNodeWriter;
import org.opendaylight.restconf.server.api.DatabindPath.OperationPath;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;

/**
 * A {@link ReplyBody} corresponding to a {@code rpc} or {@code action} invocation.
 */
@NonNullByDefault
public final class OperationOutputBody extends ReplyBody {
    private final OperationPath path;
    private final ContainerNode output;

    public OperationOutputBody(final OperationPath path, final ContainerNode output, final boolean prettyPrint) {
        super(prettyPrint);
        this.path = requireNonNull(path);
        this.output = requireNonNull(output);
        if (output.isEmpty()) {
            throw new IllegalArgumentException("output may not be empty");
        }
    }

    @VisibleForTesting
    public ContainerNode output() {
        return output;
    }

    @Override
    void writeJSON(final OutputStream out, final boolean prettyPrint) throws IOException {
        try (var nnWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
             JSONNormalizedNodeStreamWriter.createExclusiveWriter(path.databind().jsonCodecs(), path.inference(), null,
                 createJsonWriter(out, prettyPrint)), null, null)) {
            nnWriter.write(output);
        }
    }

    @Override
    void writeXML(final OutputStream out, final boolean prettyPrint) throws IOException {
        // RpcDefinition/ActionDefinition is not supported as initial codec in XMLStreamWriter, so we need to emit
        // initial output declaration.
        try (var nnWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
            XMLStreamNormalizedNodeStreamWriter.create(createXmlWriter(out, prettyPrint), path.inference()),
                null, null)) {
            nnWriter.write(output);
        }
    }
}
