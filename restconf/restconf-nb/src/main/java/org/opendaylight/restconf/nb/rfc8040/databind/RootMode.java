/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext.Composite;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * A {@link DataMode} representing the conceptual root.
 */
public record RootMode(@NonNull Inference inference, @NonNull Composite dataContext) implements DataMode {
    public RootMode {
        requireNonNull(inference);
        requireNonNull(dataContext);
    }

    @Override
    public DOMMountPoint mountPoint() {
        return null;
    }

    @Override
    public YangInstanceIdentifier path() {
        return YangInstanceIdentifier.of();
    }
}
