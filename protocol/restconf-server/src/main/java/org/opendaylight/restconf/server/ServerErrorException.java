/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import org.opendaylight.yangtools.yang.common.ErrorTag;

final class ServerErrorException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final ErrorTag errorTag;

    ServerErrorException(final ErrorTag errorTag, final String message) {
        super(message);
        this.errorTag = errorTag;
    }

    ServerErrorException(final ErrorTag errorTag, final String message, final Throwable cause) {
        super(message, cause);
        this.errorTag = errorTag;
    }

    ErrorTag errorTag() {
        return errorTag;
    }
}
