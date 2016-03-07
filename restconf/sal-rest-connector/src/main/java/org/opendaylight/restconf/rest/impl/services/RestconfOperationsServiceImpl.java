/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.services;

import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.rest.api.Draft09;
import org.opendaylight.restconf.rest.api.connector.RestSchemaController;
import org.opendaylight.restconf.rest.api.services.RestconfOperationsService;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import com.google.common.base.Predicate;

public class RestconfOperationsServiceImpl extends RestconfModul implements RestconfOperationsService {

    private static final Predicate<GroupingDefinition> GROUPING_FILTER = new Predicate<GroupingDefinition>() {

        @Override
        public boolean apply(final GroupingDefinition input) {
            return Draft09.RestConfModule.RESTCONF_GROUPING_SCHEMA_NODE.equals(input.getQName().getLocalName());
        }
    };

    public RestconfOperationsServiceImpl(final RestSchemaController restSchemaController) {
        super(restSchemaController);
    }

    @Override
    public NormalizedNodeContext getOperations(final UriInfo uriInfo) {
        return null;
    }


    @Override
    public NormalizedNodeContext getOperations(final String identifier, final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

}
