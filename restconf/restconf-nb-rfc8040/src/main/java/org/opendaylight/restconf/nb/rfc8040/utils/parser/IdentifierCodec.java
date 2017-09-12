/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Codec for identifier to serialize {@link YangInstanceIdentifier} to
 * {@link String} and deserialize {@link String} to
 * {@link YangInstanceIdentifier}.
 *
 */
public final class IdentifierCodec {

    private IdentifierCodec() {
        throw new UnsupportedOperationException("Util class.");
    }

    public static String serialize(final YangInstanceIdentifier data, final SchemaContext schemaContext) {
        return YangInstanceIdentifierSerializer.create(schemaContext, data);
    }

    public static YangInstanceIdentifier deserialize(final String data, final SchemaContext schemaContext) {
        if (data == null) {
            return YangInstanceIdentifier.builder().build();
        }
        return YangInstanceIdentifier.create(YangInstanceIdentifierDeserializer.create(schemaContext, data));
    }
}
