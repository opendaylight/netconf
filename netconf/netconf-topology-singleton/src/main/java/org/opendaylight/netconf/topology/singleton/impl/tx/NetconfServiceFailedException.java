package org.opendaylight.netconf.topology.singleton.impl.tx;

import org.opendaylight.yangtools.yang.common.OperationFailedException;
import org.opendaylight.yangtools.yang.common.RpcError;

public class NetconfServiceFailedException extends OperationFailedException {
    private static final long serialVersionUID = 1L;

    public NetconfServiceFailedException(final String message, final RpcError... errors) {
        this(message, null, errors);
    }

    public NetconfServiceFailedException(final String message, final Throwable cause, final RpcError... errors) {
        super(message, cause, errors);
    }
}
