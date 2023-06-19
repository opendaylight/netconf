/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableMetadataNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;

/**
 * This is a single component writer, which results in some amount.
 */
final class ComponentNormalizedNodeStreamWriter extends ImmutableMetadataNormalizedNodeStreamWriter {
    private ComponentNormalizedNodeStreamWriter(final State state) {
        super(state);
    }

    ComponentNormalizedNodeStreamWriter(final NormalizationResultHolder result) {
        super(result);
    }

    NormalizedNode build() {
        return popState().getDataBuilder().build();
    }
}
