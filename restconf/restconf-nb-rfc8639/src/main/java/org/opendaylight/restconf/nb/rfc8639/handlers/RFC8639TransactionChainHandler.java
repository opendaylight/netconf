/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.handlers;

import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;

public class RFC8639TransactionChainHandler extends TransactionChainHandler {

    public RFC8639TransactionChainHandler(final DOMDataBroker dataBroker) {
        super(dataBroker);
    }
}
