/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import com.google.common.base.MoreObjects.ToStringHelper;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;

/**
 * Default {@link NormalizedNodeWriterFactory}.
 */
@NonNullByDefault
final class DefaultNormalizedNodeWriterFactory extends NormalizedNodeWriterFactory {
    static final DefaultNormalizedNodeWriterFactory INSTANCE = new DefaultNormalizedNodeWriterFactory();

    private DefaultNormalizedNodeWriterFactory() {
        // Hidden on purpose
    }

    @Override
    protected NormalizedNodeWriter newWriter(final NormalizedNodeStreamWriter streamWriter) {
        return NormalizedNodeWriter.forStreamWriter(streamWriter, null);
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper;
    }
}