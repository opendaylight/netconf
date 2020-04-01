/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.OAversion;

import java.util.Objects;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.URIType;
import org.opendaylight.netconf.sal.rest.doc.swagger.CommonApiObject;
import org.opendaylight.netconf.sal.rest.doc.swagger.SwaggerObject;

public class AllModulesDocGenerator {
    private final ApiDocGeneratorDraftO2 apiDocGeneratorDraft02;
    private final ApiDocGeneratorRFC8040 apiDocGeneratorRFC8040;

    public AllModulesDocGenerator(final ApiDocGeneratorDraftO2 apiDocGeneratorDraft02,
                                  final ApiDocGeneratorRFC8040 apiDocGeneratorRFC8040) {
        this.apiDocGeneratorDraft02 = Objects.requireNonNull(apiDocGeneratorDraft02);
        this.apiDocGeneratorRFC8040 = Objects.requireNonNull(apiDocGeneratorRFC8040);
    }

    public CommonApiObject getAllModulesDoc(final UriInfo uriInfo, final URIType uriType, final OAversion oaversion) {
        final DefinitionNames definitionNames = new DefinitionNames();
        final SwaggerObject doc;
        if (uriType.equals(URIType.DRAFT02)) {
            doc = apiDocGeneratorDraft02.getAllModulesDoc(uriInfo, definitionNames, uriType, oaversion);
        } else {
            doc = apiDocGeneratorRFC8040.getAllModulesDoc(uriInfo, definitionNames, uriType, oaversion);
        }

        return BaseYangSwaggerGenerator.getAppropriateDoc(doc, oaversion);
    }
}
