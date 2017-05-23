/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf;

import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContentYangBodyWriter;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContentYinBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.PatchJsonBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.PatchXmlBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.RestconfDocumentedExceptionMapper;
import org.opendaylight.restconf.common.wrapper.services.ServicesWrapperImpl;
import org.opendaylight.restconf.jersey.providers.JsonNormalizedNodeBodyReader;
import org.opendaylight.restconf.jersey.providers.JsonToPatchBodyReader;
import org.opendaylight.restconf.jersey.providers.NormalizedNodeJsonBodyWriter;
import org.opendaylight.restconf.jersey.providers.NormalizedNodeXmlBodyWriter;
import org.opendaylight.restconf.jersey.providers.XmlNormalizedNodeBodyReader;
import org.opendaylight.restconf.jersey.providers.XmlToPatchBodyReader;

public class RestconfApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return ImmutableSet.<Class<?>>builder()
                .add(NormalizedNodeJsonBodyWriter.class).add(NormalizedNodeXmlBodyWriter.class)
                .add(JsonNormalizedNodeBodyReader.class).add(XmlNormalizedNodeBodyReader.class)
                .add(SchemaExportContentYinBodyWriter.class)
                .add(JsonToPatchBodyReader.class).add(XmlToPatchBodyReader.class)
                .add(PatchJsonBodyWriter.class).add(PatchXmlBodyWriter.class)
                .add(SchemaExportContentYangBodyWriter.class).add(RestconfDocumentedExceptionMapper.class)
                .build();
    }

    @Override
    public Set<Object> getSingletons() {
        final Set<Object> singletons = new HashSet<>();
        singletons.add(ServicesWrapperImpl.getInstance());
        return singletons;
    }
}
