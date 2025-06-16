/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.messages.netconf;

import java.io.Serial;
import java.io.Serializable;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;

public class EditConfigRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final NormalizedNodeMessage data;

    public EditConfigRequest(final NormalizedNodeMessage data) {
        this.data = data;
    }

    public NormalizedNodeMessage getNormalizedNodeMessage() {
        return data;
    }
}
