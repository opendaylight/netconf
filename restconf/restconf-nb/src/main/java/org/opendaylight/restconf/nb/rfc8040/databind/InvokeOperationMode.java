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
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * A {@link RequestUrl} in <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.4.1">Invoke Operation Mode</a>.
 */
public record InvokeOperationMode(
        @NonNull DatabindContext localDatabind,
        @NonNull YangInstanceIdentifier parentPath,
        @NonNull Inference operation,
        @Nullable DOMMountPoint mountPoint) implements POSTMode {
    public InvokeOperationMode {
        requireNonNull(localDatabind);
        requireNonNull(parentPath);
        requireNonNull(operation);
    }

    @Deprecated
    public @NonNull DatabindContext localDatabind() {
        return localDatabind;
    }
}
