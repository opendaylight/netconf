/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static java.util.Objects.requireNonNull;

import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.OAversion;
import org.opendaylight.netconf.sal.rest.doc.swagger.CommonApiObject;
import org.opendaylight.netconf.sal.rest.doc.swagger.SwaggerObject;

public class AllModulesDocGenerator {
    private final ApiDocGeneratorRFC8040 apiDocGeneratorRFC8040;

    public AllModulesDocGenerator(final ApiDocGeneratorRFC8040 apiDocGeneratorRFC8040) {
        this.apiDocGeneratorRFC8040 = requireNonNull(apiDocGeneratorRFC8040);
    }

    public CommonApiObject getAllModulesDoc(final UriInfo uriInfo, final OAversion oaversion) {
        final DefinitionNames definitionNames = new DefinitionNames();
        final SwaggerObject doc = apiDocGeneratorRFC8040.getAllModulesDoc(uriInfo, definitionNames, oaversion);

        return BaseYangSwaggerGenerator.getAppropriateDoc(doc, oaversion);
    }
}
