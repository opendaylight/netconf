/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static java.util.Objects.requireNonNull;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;

/**
 * A {@link JaxRsRestconfCallback} producing a {@link FormattableBody}.
 */
final class FormattableBodyCallback extends JaxRsRestconfCallback<FormattableBody> {
    private final @NonNull PrettyPrintParam prettyPrint;

    FormattableBodyCallback(final AsyncResponse ar, final PrettyPrintParam prettyPrint) {
        super(ar);
        this.prettyPrint = requireNonNull(prettyPrint);
    }

    @Override
    Response transform(final FormattableBody result) throws RestconfDocumentedException {
        return Response.ok().entity(new JaxRsFormattableBody(result, prettyPrint)).build();
    }
}
