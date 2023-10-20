/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.errors;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * A non-empty {@code error-path} element, expressed as a {@link YangInstanceIdentifier} and its corresponding
 * {@link EffectiveModelContext}.
 */
public final class ErrorPath implements Serializable {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final transient EffectiveModelContext context;
    private final @NonNull YangInstanceIdentifier path;

    public ErrorPath(final EffectiveModelContext context, final YangInstanceIdentifier path) {
        this.context = requireNonNull(context);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Path may not be empty");
        }
        this.path = path;
    }

    /**
     * The {@link EffectiveModelContext} in which {@link #path() should be interpreted. This method will return
     * {@code null} if this exception has been passed through Java serialization.
     *
     * @return An {@link EffectiveModelContext} or {@code null}
     */
    public @Nullable EffectiveModelContext context() {
        return context;
    }

    /**
     * The actual path, expressed as a {@link YangInstanceIdentifier}.
     *
     * @return The path
     */
    public @NonNull YangInstanceIdentifier path() {
        return path;
    }
}
