/*
 * Copyright (C) 2019 Ericsson Software Technology AB. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.utils;

import java.io.Serial;
import org.opendaylight.mdsal.dom.api.DOMActionException;

/**
 * Exception thrown during remote action invocation in Odl-Cluster environment.
 */
public class ClusteringActionException extends DOMActionException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructor for {@code ClusteringActionException}.
     *
     * @param message String
     */
    public ClusteringActionException(final String message) {
        super(message);
    }

    /**
     * Constructor for {@code ClusteringActionException}.
     *
     * @param message String
     * @param cause Throwable
     */
    public ClusteringActionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}