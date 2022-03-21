/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.impl;

import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Application;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContentYangBodyWriter;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContentYinBodyWriter;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaRetrievalServiceImpl;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.StatisticsRestconfServiceWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Deprecated(since = "2.0.12")
public class RestconfApplication extends Application {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfApplication.class);

    private final ControllerContext controllerContext;
    private final StatisticsRestconfServiceWrapper statsServiceWrapper;

    @Inject
    public RestconfApplication(final ControllerContext controllerContext,
            final StatisticsRestconfServiceWrapper statsServiceWrapper) {
        this.controllerContext = controllerContext;
        this.statsServiceWrapper = statsServiceWrapper;
        LOG.warn("Pre-standard version of RESTCONF activated. Please note that this implementation is considered "
            + "obsoleve and WILL BE REMOVED IN THE NEXT MAJOR RELEASE. Please use the RFC8040-compliant "
            + "implementation instead.");
    }

    @Override
    public Set<Class<?>> getClasses() {
        return ImmutableSet.<Class<?>>builder()
                .add(PatchJsonBodyWriter.class)
                .add(PatchXmlBodyWriter.class)
                .add(NormalizedNodeJsonBodyWriter.class)
                .add(NormalizedNodeXmlBodyWriter.class)
                .add(SchemaExportContentYinBodyWriter.class)
                .add(SchemaExportContentYangBodyWriter.class)
                .build();
    }

    @Override
    public Set<Object> getSingletons() {
        final Set<Object> singletons = new HashSet<>();
        final SchemaRetrievalServiceImpl schemaRetrieval = new SchemaRetrievalServiceImpl(controllerContext);
        singletons.add(schemaRetrieval);
        singletons.add(new RestconfCompositeWrapper(statsServiceWrapper, schemaRetrieval));
        singletons.add(new RestconfDocumentedExceptionMapper(controllerContext));
        singletons.add(new XmlNormalizedNodeBodyReader(controllerContext));
        singletons.add(new JsonNormalizedNodeBodyReader(controllerContext));
        singletons.add(new XmlToPatchBodyReader(controllerContext));
        singletons.add(new JsonToPatchBodyReader(controllerContext));
//        singletons.add(StructuredDataToXmlProvider.INSTANCE);
//        singletons.add(StructuredDataToJsonProvider.INSTANCE);
//        singletons.add(JsonToCompositeNodeProvider.INSTANCE);
//        singletons.add(XmlToCompositeNodeProvider.INSTANCE);
        return singletons;
    }

}
