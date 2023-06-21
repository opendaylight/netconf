/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.messages.netconf;

import java.io.Serial;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadActorMessage;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class GetRequest implements ReadActorMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    private final YangInstanceIdentifier path;

    public GetRequest(final YangInstanceIdentifier path) {
        this.path = path;
    }

    public YangInstanceIdentifier getPath() {
        return path;
    }
}
