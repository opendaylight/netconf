/*
 * Copyright (c) 2017 Pantheon technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.services.simple.api;

/**
 * Allow update of handlers in web application services, if needed.
 */
public interface UpdateHandlers {

    /**
     * Update method for handlers in specific service (resource) of web application.
     * Has to be implemented as synchronized to avoid conflict of update variables in multithreaded application.
     *
     * @param handlers
     *            array of handlers
     */
    default void updateHandlers(final Object... handlers) {
        throw new UnsupportedOperationException("This method it's not allowed for this service by default.");
    }
}
