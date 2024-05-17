/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import org.eclipse.jdt.annotation.NonNull;

/**
 * Factory building {@link AuthHandler} instances to serve requests authentication and
 * authorization for new connections established.
 */
@FunctionalInterface
public interface AuthHandlerFactory {

    /**
     * Builds {@link AuthHandler} instance.
     *
     * @return handler instance
     */
    @NonNull AuthHandler<?> create();
}
