/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.utils;

import java.io.Serial;
import org.opendaylight.mdsal.dom.api.DOMRpcException;

public class ClusteringRpcException extends DOMRpcException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ClusteringRpcException(final String message) {
        super(message);
    }

    public ClusteringRpcException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
