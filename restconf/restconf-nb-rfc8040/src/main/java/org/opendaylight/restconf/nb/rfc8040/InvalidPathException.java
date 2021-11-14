/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

/**
 * Exception thrown when {@link ApiSegment} cannot be created.
 */
public final class InvalidPathException extends Exception {
    private static final long serialVersionUID = 1L;

    InvalidPathException(final String message) {
        super(requireNonNull(message));
    }
}
