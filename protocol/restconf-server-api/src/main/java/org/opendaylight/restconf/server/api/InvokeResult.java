/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.FormattableBody;

/**
 * Result of a {@code POST} request resulting in an operation invocation, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.6">RFC8040 Operation Resource</a> and
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.4.2">RFC8040 Invoke Operation Mode</a>.
 *
 * @param output Non-empty operation output, or {@code null}
 */
public record InvokeResult(@Nullable FormattableBody output) implements DataPostResult {
    /**
     * Empty instance. Prefer this to creating one with {@code output}.
     */
    public static final @NonNull InvokeResult EMPTY = new InvokeResult(null);
}