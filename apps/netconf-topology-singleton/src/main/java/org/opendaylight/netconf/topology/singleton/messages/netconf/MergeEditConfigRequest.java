/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.messages.netconf;

import java.io.Serial;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;

public class MergeEditConfigRequest extends EditConfigRequest {
    @Serial
    private static final long serialVersionUID = 1L;

    public MergeEditConfigRequest(LogicalDatastoreType store, NormalizedNodeMessage data,
                                  EffectiveOperation defaultOperation) {
        super(store, data, defaultOperation);
    }
}
