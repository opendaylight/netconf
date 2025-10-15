/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.AbstractServerRequest;
import org.opendaylight.restconf.server.api.YangErrorsBody;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link AbstractServerRequest} which maps reported errors using {@link ErrorTagMapping}.
 */
@NonNullByDefault
public abstract class MappingServerRequest<T> extends AbstractServerRequest<T> {
    private static final Logger LOG = LoggerFactory.getLogger(MappingServerRequest.class);

    private final ErrorTagMapping errorTagMapping;

    protected MappingServerRequest(final @Nullable Principal principal, final QueryParameters queryParameters,
            final PrettyPrintParam defaultPrettyPrint, final ErrorTagMapping errorTagMapping) {
        super(principal, queryParameters, defaultPrettyPrint);
        this.errorTagMapping = requireNonNull(errorTagMapping);
    }

    @Override
    public final void failWith(final ErrorTag errorTag, final FormattableBody body) {
        onFailure(errorTagMapping.statusOf(errorTag), body);
    }

    @Override
    protected final void onFailure(final YangErrorsBody body) {
        final var errors = body.errors();
        final var statusCodes = errors.stream()
            .map(restconfError -> errorTagMapping.statusOf(restconfError.tag()))
            .distinct()
            .toList();
        if (statusCodes.size() > 1) {
            LOG.warn("""
                An unexpected error occurred during translation to response: Different status codes have been found in
                appended error entries: {}. The first error entry status code is chosen for response.""",
                statusCodes, new Throwable());
        }
        onFailure(statusCodes.getFirst(), body);
    }

    protected abstract void onFailure(HttpStatusCode status, FormattableBody body);
}
