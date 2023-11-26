/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;

/**
 * A service providing access to a {@link DatabindContext}.
 */
@NonNullByDefault
public interface DatabindProvider {
    /**
     * Acquire current {@link DatabindContext}.
     *
     * @return Current {@link DatabindContext}
     */
    DatabindContext currentContext();
}
