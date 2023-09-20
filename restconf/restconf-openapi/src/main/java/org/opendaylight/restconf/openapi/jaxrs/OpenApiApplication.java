/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.jaxrs;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import java.util.Set;
import javax.ws.rs.core.Application;
import org.opendaylight.restconf.openapi.api.OpenApiService;

// FIXME: hide this class
public final class OpenApiApplication extends Application {
    private final OpenApiService openApiService;

    public OpenApiApplication(final OpenApiService openApiService) {
        this.openApiService = requireNonNull(openApiService);
    }

    @Override
    public Set<Object> getSingletons() {
        return Set.of(openApiService, new JaxbContextResolver(), new JacksonJaxbJsonProvider());
    }
}
