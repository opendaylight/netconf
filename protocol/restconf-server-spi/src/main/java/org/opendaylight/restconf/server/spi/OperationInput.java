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
import org.opendaylight.netconf.databind.DatabindPath.OperationPath;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * Input to an operation invocation.
 */
@NonNullByDefault
public record OperationInput(OperationPath path, ContainerNode input) {
    public OperationInput {
        requireNonNull(path);
        requireNonNull(input);
    }
}