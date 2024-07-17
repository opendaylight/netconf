/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.NonNull;

/**
 * Text formatting parameters.
 *
 * @param leafNodesOnly {@code true} if this query should only notify about leaf node changes
 * @param skipData {@code true} if this query should notify about changes with data
 * @param changedLeafNodesOnly {@code true} if this query should only notify about leaf node changes and report only
 *                             changed nodes
 * @param childNodesOnly {@code true} if this query should only notify about child node changes
 */
public record TextParameters(
        boolean leafNodesOnly,
        boolean skipData,
        boolean changedLeafNodesOnly,
        boolean childNodesOnly) {
    public static final @NonNull TextParameters EMPTY = new TextParameters(false, false, false, false);
}