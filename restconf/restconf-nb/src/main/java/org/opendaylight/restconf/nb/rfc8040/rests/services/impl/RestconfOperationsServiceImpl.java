/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import javax.ws.rs.Path;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfOperationsService;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
/**
 * Implementation of {@link RestconfOperationsService}.
 */

@Path("/")
public class RestconfOperationsServiceImpl implements RestconfOperationsService {
    private final DatabindProvider databindProvider;

    /**
     * Set {@link DatabindProvider} for getting actual {@link EffectiveModelContext}.
     *
     * @param databindProvider a {@link DatabindProvider}
     * @param mountPointService a {@link DOMMountPointService}
     */
    public RestconfOperationsServiceImpl(final DatabindProvider databindProvider,
            final DOMMountPointService mountPointService) {
        this.databindProvider = requireNonNull(databindProvider);
    }

    @Override
    public String getOperationsJSON() {
        return OperationsContent.JSON.bodyFor(databindProvider.currentContext().modelContext());
    }

    @Override
    public String getOperationJSONByIdentifier(final String identifier) {
        return OperationsContent.JSON.bodyFor(databindProvider.currentContext().modelContext(), identifier);
    }

    @Override
    public String getOperationsXML() {
        return OperationsContent.XML.bodyFor(databindProvider.currentContext().modelContext());
    }

    @Override
    public String getOperationXMLByIdentifier(final String identifier) {
        return OperationsContent.XML.bodyFor(databindProvider.currentContext().modelContext(), identifier);
    }
}
