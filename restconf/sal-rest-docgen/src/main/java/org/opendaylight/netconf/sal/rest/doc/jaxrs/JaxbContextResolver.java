/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.jaxrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import org.opendaylight.netconf.sal.rest.doc.swagger.ApiDeclaration;

@Provider
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class JaxbContextResolver implements ContextResolver<ObjectMapper> {

    private final ObjectMapper ctx;

    public JaxbContextResolver() {
        ctx = new ObjectMapper();
    }

    @Override
    public ObjectMapper getContext(final Class<?> klass) {
        if (ApiDeclaration.class.isAssignableFrom(klass)) {
            return ctx;
        }

        return null; // must return null so that JAX-RS can continue context search
    }
}
