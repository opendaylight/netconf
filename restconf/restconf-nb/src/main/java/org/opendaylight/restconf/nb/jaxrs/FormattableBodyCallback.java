/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;

/**
 * A {@link JaxRsRestconfCallback} producing a {@link FormattableBody}.
 */
final class FormattableBodyCallback extends JaxRsRestconfCallback<FormattableBody> {
    FormattableBodyCallback(final AsyncResponse ar) {
        super(ar);
    }

    @Override
    Response transform(final FormattableBody result) throws RestconfDocumentedException {
        return Response.ok().entity(result).build();
    }
}
