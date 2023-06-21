/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.netconf;

import java.io.Serial;
import org.opendaylight.yangtools.yang.common.OperationFailedException;
import org.opendaylight.yangtools.yang.common.RpcError;

public class NetconfServiceFailedException extends OperationFailedException {
    @Serial
    private static final long serialVersionUID = 1L;

    public NetconfServiceFailedException(final String message, final RpcError... errors) {
        this(message, null, errors);
    }

    public NetconfServiceFailedException(final String message, final Throwable cause, final RpcError... errors) {
        super(message, cause, errors);
    }
}
