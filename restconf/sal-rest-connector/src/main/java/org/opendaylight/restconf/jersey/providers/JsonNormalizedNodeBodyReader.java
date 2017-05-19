/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.jersey.providers;

import com.google.gson.stream.JsonReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import org.opendaylight.restconf.Rfc8040;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

@Provider
@Consumes({ Rfc8040.MediaTypes.DATA + RestconfConstants.JSON, MediaType.APPLICATION_JSON })
public class JsonNormalizedNodeBodyReader extends AbstractNormalizedNodeBodyReader {

    @Override
    void readStream(final InputStream entityStream, final NormalizedNodeStreamWriter writer,
            final SchemaContext schemaContext, final SchemaNode parentSchema, final SchemaNode schema) {
        final JsonParserStream jsonParser = JsonParserStream.create(writer, schemaContext, parentSchema);
        final JsonReader reader = new JsonReader(new InputStreamReader(entityStream));
        jsonParser.parse(reader);
    }
}

