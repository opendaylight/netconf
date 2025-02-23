/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.databind.DatabindPath.OperationPath;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

/**
 * Access to an {code rpc}'s or an {@code action}'s input.
 */
public abstract sealed class OperationInputBody extends RequestBody
        permits JsonOperationInputBody, XmlOperationInputBody {
    OperationInputBody(final InputStream inputStream) {
        super(inputStream);
    }

    /**
     * Stream the {@code input} into a {@link NormalizedNodeStreamWriter}.
     *
     * @param path The {@link OperationPath} of the operation invocation
     * @return The document body, or an empty container node
     * @throws ServerException when an I/O error occurs
     */
    public final @NonNull ContainerNode toContainerNode(final @NonNull OperationPath path) throws ServerException {
        try (var is = new PushbackInputStream(consume())) {
            final var firstByte = is.read();
            if (firstByte == -1) {
                return ImmutableNodes.newContainerBuilder()
                    .withNodeIdentifier(new NodeIdentifier(path.inputStatement().argument()))
                    .build();
            }
            is.unread(firstByte);

            final var holder = new NormalizationResultHolder();
            try (var streamWriter = ImmutableNormalizedNodeStreamWriter.from(holder)) {
                streamTo(path, is, streamWriter);
            }
            return (ContainerNode) holder.getResult().data();
        } catch (IOException e) {
            throw newProtocolMalformedMessageServerException(path, "Invalid input", e);
        }
    }

    abstract void streamTo(@NonNull OperationPath path, @NonNull InputStream inputStream,
        @NonNull NormalizedNodeStreamWriter writer) throws ServerException;
}
