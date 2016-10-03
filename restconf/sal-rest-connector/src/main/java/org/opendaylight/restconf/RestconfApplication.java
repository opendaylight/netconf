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
import org.opendaylight.netconf.sal.rest.impl.JsonNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeJsonBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeXmlBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.PATCHJsonBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.PATCHXmlBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.RestconfDocumentedExceptionMapper;
import org.opendaylight.netconf.sal.rest.impl.XmlNormalizedNodeBodyReader;
import org.opendaylight.restconf.common.wrapper.services.ServicesWrapperImpl;
import org.opendaylight.restconf.utils.patch.JsonToPATCHBodyReader;
import org.opendaylight.restconf.utils.patch.XmlToPATCHBodyReader;

public class RestconfApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return ImmutableSet.<Class<?>> builder().add(NormalizedNodeJsonBodyWriter.class)
                .add(NormalizedNodeXmlBodyWriter.class).add(JsonNormalizedNodeBodyReader.class)
                .add(XmlNormalizedNodeBodyReader.class).add(SchemaExportContentYinBodyWriter.class)
                .add(JsonToPATCHBodyReader.class).add(XmlToPATCHBodyReader.class)
                .add(PATCHJsonBodyWriter.class).add(PATCHXmlBodyWriter.class)
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
