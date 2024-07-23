/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;

/**
 * A {@link JaxRsServerRequest} resulting in a {@link FormattableBody}.
 */
@NonNullByDefault
final class FormattableJaxRsServerRequest extends JaxRsServerRequest<FormattableBody> {
    FormattableJaxRsServerRequest(final PrettyPrintParam defaultPrettyPrint, final AsyncResponse ar) {
        super(defaultPrettyPrint, ar);
    }

    @Override
    Response transform(final FormattableBody result) {
        return Response.ok().entity(new JaxRsFormattableBody(result, prettyPrint())).build();
    }
}
