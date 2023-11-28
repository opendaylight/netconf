/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.server.api.OperationsPostPath;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;

/**
 * Access to an {code rpc}'s or an {@code action}'s input.
 */
public abstract sealed class OperationInputBody extends AbstractBody
        permits JsonOperationInputBody, XmlOperationInputBody {
    OperationInputBody(final InputStream inputStream) {
        super(inputStream);
    }

    /**
     * Stream the {@code input} into a {@link NormalizedNodeStreamWriter}.
     *
     * @param path The {@link OperationsPostPath} of the operation invocation
     * @return The document body, or an empty container node
     * @throws IOException when an I/O error occurs
     */
    public @NonNull ContainerNode toContainerNode(final @NonNull OperationsPostPath path) throws IOException {
        try (var is = new PushbackInputStream(acquireStream())) {
            final var firstByte = is.read();
            if (firstByte == -1) {
                return ImmutableNodes.containerNode(path.inputQName());
            }
            is.unread(firstByte);

            final var holder = new NormalizationResultHolder();
            try (var streamWriter = ImmutableNormalizedNodeStreamWriter.from(holder)) {
                streamTo(path, is, streamWriter);
            }
            return (ContainerNode) holder.getResult().data();
        }
    }

    abstract void streamTo(@NonNull OperationsPostPath path, @NonNull InputStream inputStream,
        @NonNull NormalizedNodeStreamWriter writer) throws IOException;
}
