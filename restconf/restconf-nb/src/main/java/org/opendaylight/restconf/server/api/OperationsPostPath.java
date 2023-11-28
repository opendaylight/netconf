/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.ActionEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * An {@link ApiPath} subpath of {@code /operations} {@code POST} HTTP operation, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.6">RFC8040 Operation Resource</a> and
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.4.2">RFC8040 Invoke Operation Mode</a>.
 *
 * @param databind Associated {@link DatabindContext}
 * @param operation Associated {@link Inference} pointing to the {@link EffectiveStatement} of an {@code rpc} or an
 *                  {@code action} invocation, inference pointing to the statement</li>
 * @see OperationsPostResult
 */
@NonNullByDefault
public record OperationsPostPath(DatabindContext databind, Inference operation) implements DatabindAware {
    public OperationsPostPath {
        requireNonNull(databind);
        requireNonNull(operation);
    }

    public Inference input() {
        final var stack = operation.toSchemaInferenceStack();
        stack.enterDataTree(inputQName(stack));
        return stack.toInference();
    }

    public QName inputQName() {
        return inputQName(operation.toSchemaInferenceStack());
    }

    private static QName inputQName(final SchemaInferenceStack stack) {
        final var stmt = stack.currentStatement();
        if (stmt instanceof RpcEffectiveStatement rpc) {
            return rpc.input().argument();
        } else if (stmt instanceof ActionEffectiveStatement action) {
            return action.input().argument();
        } else {
            throw new IllegalStateException(stack + " does not identify an 'rpc' nor an 'action' statement");
        }
    }
}
