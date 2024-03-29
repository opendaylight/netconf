/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.api;

import java.io.Serial;

/**
 * This exception is thrown by
 * {@link NetconfSessionListener#onMessage} to indicate fatal
 * communication problem after which the session should be closed.
 */
public class NetconfDeserializerException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    public NetconfDeserializerException(final String message) {
        super(message);
    }

    public NetconfDeserializerException(final String message, final Exception exception) {
        super(message, exception);
    }
}
