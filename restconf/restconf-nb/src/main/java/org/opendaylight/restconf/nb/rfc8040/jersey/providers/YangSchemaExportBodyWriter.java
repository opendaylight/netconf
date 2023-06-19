/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.SchemaExportContext;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;

@Provider
@Produces(YangConstants.RFC6020_YANG_MEDIA_TYPE)
public class YangSchemaExportBodyWriter extends AbstractSchemaExportBodyWriter {
    @Override
    public void writeTo(final SchemaExportContext context, final Class<?> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType,
            final MultivaluedMap<String, Object> httpHeaders, final OutputStream entityStream) throws IOException {
        final Module module = context.getModule();
        final SourceIdentifier sourceId = new SourceIdentifier(module.getName(),
                module.getQNameModule().getRevision().map(Revision::toString).orElse(null));
        final YangTextSchemaSource yangTextSchemaSource;
        try {
            yangTextSchemaSource = context.getSourceProvider().getSource(sourceId).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new WebApplicationException("Unable to retrieve source from SourceProvider.", e);
        }
        yangTextSchemaSource.asByteSource(StandardCharsets.UTF_8).copyTo(entityStream);
    }
}
