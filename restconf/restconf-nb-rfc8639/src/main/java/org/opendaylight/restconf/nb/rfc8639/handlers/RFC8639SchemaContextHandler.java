/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.handlers;

import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;

public class RFC8639SchemaContextHandler extends SchemaContextHandler {

    public RFC8639SchemaContextHandler(
            final TransactionChainHandler transactionChainHandler,
            final DOMSchemaService domSchemaService) {
        super(transactionChainHandler, domSchemaService);
    }
}
