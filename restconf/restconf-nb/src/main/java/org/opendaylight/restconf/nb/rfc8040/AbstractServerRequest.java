/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.common.ErrorTag;

/**
 * Abstract base class for northbound implementations of {@link ServerRequest}. This class exists here to carry
 * {@link ErrorTagMapping}.
 */
@NonNullByDefault
public abstract class AbstractServerRequest<T> extends ServerRequest<T> {
    private final ErrorTagMapping errorTagMapping;

    protected AbstractServerRequest(final QueryParameters queryParameters, final PrettyPrintParam defaultPrettyPrint,
            final ErrorTagMapping errorTagMapping) {
        super(queryParameters, defaultPrettyPrint);
        this.errorTagMapping = requireNonNull(errorTagMapping);
    }

    protected final HttpStatusCode statusOf(final ErrorTag errorTag) {
        return errorTagMapping.statusOf(errorTag);
    }
}
