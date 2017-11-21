/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.md.sal.rest.schema;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import org.opendaylight.restconf.common.schema.SchemaExportContext;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;

@Provider
@Produces({ SchemaRetrievalService.YANG_MEDIA_TYPE })
public class SchemaExportContentYangBodyWriter implements MessageBodyWriter<SchemaExportContext> {

    @Override
    public boolean isWriteable(final Class<?> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType) {
        return type.equals(SchemaExportContext.class);
    }

    @Override
    public long getSize(final SchemaExportContext context, final Class<?> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(final SchemaExportContext context, final Class<?> type, final Type genericType,
            final Annotation[] annotations, final MediaType mediaType,
            final MultivaluedMap<String, Object> httpHeaders, final OutputStream entityStream) throws IOException,
            WebApplicationException {
        final RevisionSourceIdentifier sourceId = RevisionSourceIdentifier.create(context.getModule().getName(),
                context.getModule().getQNameModule().getFormattedRevision());
        final YangTextSchemaSource yangTextSchemaSource;
        try {
            yangTextSchemaSource = context.getSourceProvider().getSource(sourceId).checkedGet();
        } catch (SchemaSourceException e) {
            throw new WebApplicationException("Unable to retrieve source from SourceProvider.", e);
        }
        yangTextSchemaSource.copyTo(entityStream);
    }
}
