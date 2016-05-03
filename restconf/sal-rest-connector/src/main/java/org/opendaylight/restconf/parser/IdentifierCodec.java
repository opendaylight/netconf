/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.parser;

import org.opendaylight.restconf.parser.builder.YangInstanceIdentifierDeserializerBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.codec.InstanceIdentifierCodec;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class IdentifierCodec implements InstanceIdentifierCodec<String> {


    private final SchemaContext schemaContext;
    private Object mountPoint;

    public IdentifierCodec(final SchemaContext schemaContext) {
        this.schemaContext = schemaContext;
    }

    @Override
    public String serialize(final YangInstanceIdentifier data) {
        final YangInstanceIdentifierSerializerBuilder serializer = new YangInstanceIdentifierSerializerBuilder(
                this.schemaContext, data);
        return serializer.build();
    }

    @Override
    public YangInstanceIdentifier deserialize(final String data) {
        if (data == null) {
            return YangInstanceIdentifier.builder().build();
        }
        final YangInstanceIdentifierDeserializerBuilder yIISBuilder = new YangInstanceIdentifierDeserializerBuilder(
                this.schemaContext, data);
        return YangInstanceIdentifier.create(yIISBuilder.build());
    }
}
