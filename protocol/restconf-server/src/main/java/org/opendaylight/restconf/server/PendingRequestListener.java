/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * An entity listening to {@link PendingRequest}s completions.
 */
@Beta
@NonNullByDefault
public interface PendingRequestListener {

    void requestComplete(PendingRequest<?> request, Response response);

    void requestFailed(PendingRequest<?> request, Exception cause);
}
