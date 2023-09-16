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
 * A shared resource mode for {@code GET}, {@code PUT} and {@code PATCH} operations, as they do their own thing.
 */
@SuppressWarnings("deprecation")
public record ResourceMode(
        @NonNull DatabindContext localDatabind,
        @NonNull Inference inference,
        @NonNull YangInstanceIdentifier path,
        @Nullable DOMMountPoint mountPoint) implements RequestMode {
    public ResourceMode {
        requireNonNull(localDatabind);
        requireNonNull(inference);
        requireNonNull(path);
    }
}
