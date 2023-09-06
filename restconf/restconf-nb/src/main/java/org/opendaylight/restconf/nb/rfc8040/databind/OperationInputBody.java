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
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.model.api.stmt.ActionEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.InputEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

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
     * @param inference An {@link Inference} of parent {@code rpc} or {@code action} statement
     * @param writer Target writer
     * @throws IOException when an I/O error occurs
     */
    public @NonNull ContainerNode toContainerNode(final @NonNull Inference inference) throws IOException {
        try (var is = new PushbackInputStream(acquireStream())) {
            final var firstByte = is.read();
            if (firstByte == -1) {
                return emptyInput(inference);
            }
            is.unread(firstByte);

          final var holder = new NormalizationResultHolder();
          try (var streamWriter = ImmutableNormalizedNodeStreamWriter.from(holder)) {
              streamTo(is, inference, streamWriter);
          }
          return (ContainerNode) holder.getResult().data();
        }
    }

    abstract void streamTo(@NonNull InputStream inputStream, @NonNull Inference inference,
        @NonNull NormalizedNodeStreamWriter writer) throws IOException;

    private static @NonNull ContainerNode emptyInput(final Inference inference) {
        return Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(extractInput(inference).argument()))
            .build();
    }

    private static @NonNull InputEffectiveStatement extractInput(final Inference inference) {
        final var stmt = inference.toSchemaInferenceStack().currentStatement();
        if (stmt instanceof RpcEffectiveStatement rpc) {
            return rpc.input();
        } else if (stmt instanceof ActionEffectiveStatement action) {
            return action.streamEffectiveSubstatements(InputEffectiveStatement.class).findFirst().orElseThrow();
        } else {
            throw new IllegalStateException(inference + " does not identify an 'rpc' statement");
        }
    }
}
