/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Codec for identifier to serialize {@link YangInstanceIdentifier} to
 * {@link String} and deserialize {@link String} to
 * {@link YangInstanceIdentifier}.
 *
 */
public final class IdentifierCodec {
    private IdentifierCodec() {
        // Hidden on purpose
    }

    public static String serialize(final YangInstanceIdentifier data, final EffectiveModelContext schemaContext) {
        return YangInstanceIdentifierSerializer.create(schemaContext, data);
    }

    public static YangInstanceIdentifier deserialize(final String data, final EffectiveModelContext schemaContext) {
        return data == null ? YangInstanceIdentifier.of()
            : YangInstanceIdentifierDeserializer.create(schemaContext, data).path;
    }
}
