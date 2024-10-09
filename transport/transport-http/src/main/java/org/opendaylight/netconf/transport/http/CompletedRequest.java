/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A {@link PreparedRequest} which is already complete, i.e. it is also a {@link Response}.
 */
@Beta
@NonNullByDefault
public non-sealed interface CompletedRequest extends PreparedRequest {
    /**
     * Return the result of this request.
     *
     * @return A {@link Response}
     */
    Response asResponse();
}
