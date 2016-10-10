/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages;

import java.io.Serializable;
import org.opendaylight.yangtools.yang.common.QName;

public class InvokeRpcMessage implements Serializable {

    // Schema types parameters
    private final Iterable<QName> path;
    private final boolean absolute;

    private final NormalizedNodeMessage normalizedNodeMessage;

    public InvokeRpcMessage(final Iterable<QName> path, final boolean absolute,
                            final NormalizedNodeMessage normalizedNodeMessage) {
        this.path = path;
        this.absolute = absolute;
        this.normalizedNodeMessage = normalizedNodeMessage;
    }

    public Iterable<QName> getPath() {
        return path;
    }

    public boolean isAbsolute() {
        return absolute;
    }

    public NormalizedNodeMessage getNormalizedNodeMessage() {
        return normalizedNodeMessage;
    }
}
