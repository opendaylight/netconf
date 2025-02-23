/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.common.DatabindContext;

/**
 * Common interface for contracts that provide a {@link DatabindContext} instance via {@link #databind()}.
 */
@NonNullByDefault
public interface DatabindAware {
    /**
     * Returns the associated DatabindContext.
     *
     * @return the associated DatabindContext
     */
    DatabindContext databind();
}
