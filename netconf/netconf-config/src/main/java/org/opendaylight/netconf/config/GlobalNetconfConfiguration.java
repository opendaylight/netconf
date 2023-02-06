/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Intermediate interface for decomposition of {@link Configuration}, which decomposes three distinct configurations,
 * each of which needs a separate lifecycle.
 */
@NonNullByDefault
sealed interface GlobalNetconfConfiguration permits DefaultGlobalNetconfConfiguration {
    /**
     * Return the Configuration attached to this instance.
     *
     * @return A {@link Configuration} object.
     */
    Configuration configuration();
}
