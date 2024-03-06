/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.stmt.ActionEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

abstract class AbstractNormalizedNodeBodyWriter implements MessageBodyWriter<NormalizedNodePayload> {
    @Override
    public final boolean isWriteable(final Class<?> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType) {
        return type.equals(NormalizedNodePayload.class);
    }

    @Override
    public final void writeTo(final NormalizedNodePayload context, final Class<?> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String, Object> httpHeaders,
            final OutputStream entityStream) throws IOException {
        final var output = requireNonNull(entityStream);
        final var stack = context.inference().toSchemaInferenceStack();
        // FIXME: this dispatch is here to handle codec transition to 'output', but that should be completely okay with
        //        the instantiation path we are using (based in Inference).
        if (!stack.isEmpty()) {
            final var stmt = stack.currentStatement();
            if (stmt instanceof RpcEffectiveStatement rpc) {
                stack.enterSchemaTree(rpc.output().argument());
                writeOperationOutput(stack, context.writerParameters(), (ContainerNode) context.data(), output);
            } else if (stmt instanceof ActionEffectiveStatement action) {
                stack.enterSchemaTree(action.output().argument());
                writeOperationOutput(stack, context.writerParameters(), (ContainerNode) context.data(), output);
            } else {
                writeData(stack, context.writerParameters(), context.data(), output);
            }
        } else {
            writeRoot(stack, context.writerParameters(), context.data(), output);
        }
    }

    abstract void writeOperationOutput(@NonNull SchemaInferenceStack stack, @NonNull QueryParameters writerParameters,
        @NonNull ContainerNode output, @NonNull OutputStream entityStream) throws IOException;

    abstract void writeData(@NonNull SchemaInferenceStack stack, @NonNull QueryParameters writerParameters,
        @NonNull NormalizedNode data, @NonNull OutputStream entityStream) throws IOException;

    abstract void writeRoot(@NonNull SchemaInferenceStack stack, @NonNull QueryParameters writerParameters,
        @NonNull NormalizedNode data, @NonNull OutputStream entityStream) throws IOException;
}
