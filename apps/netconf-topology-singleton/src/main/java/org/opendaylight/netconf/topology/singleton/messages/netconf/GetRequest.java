/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.messages.netconf;

import java.io.Serial;
import org.opendaylight.netconf.databind.DatabindPath;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadActorMessage;
import org.opendaylight.restconf.server.api.DataGetParams;

public class GetRequest implements ReadActorMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    private final DatabindPath.Data path;
    private final DataGetParams params;

    public GetRequest(final DatabindPath.Data path, DataGetParams params) {
        this.path = path;
        this.params = params;
    }

    public DatabindPath.Data getPath() {
        return path;
    }

    public DataGetParams getParams() {
        return params;
    }
}
