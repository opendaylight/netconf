/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * RESTCONF {@code /operations} content for a {@code POST} operation as per
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.6">RFC8040 Operation Resource</a> and
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.4.2">RFC8040 Invoke Operation Mode</a>.
 *
 * @param databind Associated {@link DatabindContext}
 * @param operation An {@link Inference} pointing to the invoked operation
 * @param output Operation output, or {@code null} if output would be empty
 * @see OperationsPostPath
 */
public record OperationsPostResult(
        @NonNull DatabindContext databind,
        @NonNull Inference operation,
        @Nullable ContainerNode output) implements DatabindAware {
    public OperationsPostResult {
        requireNonNull(databind);
        requireNonNull(operation);
        if (output != null && output.isEmpty()) {
            output = null;
        }
    }
}