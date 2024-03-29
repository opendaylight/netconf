/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.DatabindAware;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.OperationsPostResult;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * Input to an operation invocation.
 */
@NonNullByDefault
public record OperationInput(DatabindContext databind, Inference operation, ContainerNode input)
        implements DatabindAware {
    public OperationInput {
        requireNonNull(databind);
        requireNonNull(operation);
        requireNonNull(input);
    }

    /**
     * Create an {@link OperationsPostResult} with equal {@link #databind()} and {@link #operation()}.
     *
     * @param output Output payload
     * @return An {@link OperationsPostResult}
     */
    public OperationsPostResult newOperationOutput(final @Nullable ContainerNode output) {
        return new OperationsPostResult(databind, operation, output);
    }
}