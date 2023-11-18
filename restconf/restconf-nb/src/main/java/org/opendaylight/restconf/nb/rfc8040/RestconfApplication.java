/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import java.util.Set;
import javax.ws.rs.core.Application;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.JsonNormalizedNodeBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.JsonPatchStatusBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.XmlNormalizedNodeBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.XmlPatchStatusBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.YangSchemaExportBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.YinSchemaExportBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors.RestconfDocumentedExceptionMapper;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfImpl;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfSchemaServiceImpl;
import org.opendaylight.restconf.server.api.RestconfServer;

final class RestconfApplication extends Application {
    private final Set<Object> singletons;

    RestconfApplication(final DatabindProvider databindProvider, final RestconfServer server,
            final DOMMountPointService mountPointService, final DOMSchemaService domSchemaService) {
        singletons = Set.of(
            new RestconfDocumentedExceptionMapper(databindProvider),
            new RestconfImpl(server),
            new RestconfSchemaServiceImpl(domSchemaService, mountPointService));
    }

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(
            JsonNormalizedNodeBodyWriter.class, XmlNormalizedNodeBodyWriter.class,
            YinSchemaExportBodyWriter.class, YangSchemaExportBodyWriter.class,
            JsonPatchStatusBodyWriter.class, XmlPatchStatusBodyWriter.class);
    }

    @Override
    public Set<Object> getSingletons() {
        return singletons;
    }
}
