/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import java.text.ParseException;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.PathParam;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;

/**
 * A JAX-RS parsing bridge to {@link ApiPath}.
 */
@NonNullByDefault
final class JaxRsApiPath {
    final ApiPath apiPath;

    /**
     * Default constructor for {@link PathParam} integration.
     *
     * @param str Path parameter value
     * @throws NullPointerException if {@code str} is {@code null}
     * @throws BadRequestException if {@code str} cannmot be interpreted as an {@link ApiPath}
     */
    JaxRsApiPath(final String str) {
        try {
            apiPath = ApiPath.parseUrl(str);
        } catch (ParseException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public String toString() {
        return apiPath.toString();
    }
}
