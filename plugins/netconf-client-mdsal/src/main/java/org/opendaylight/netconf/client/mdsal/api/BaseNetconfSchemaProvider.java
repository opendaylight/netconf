/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A provider of {@link BaseNetconfSchema} instances.
 */
@Beta
@NonNullByDefault
public interface BaseNetconfSchemaProvider {
    /**
     * Return a {@link BaseNetconfSchema} corresponding to a set of capabilities advertized by a NETCONF device.
     *
     * @param sessionPreferences the set of advertized capabilities
     * @return A {@link BaseNetconfSchema}
     * @throws NullPointerException if {@code capabilityURNs} is {@code null}
     */
    // FIXME: return ListenableFuture
    BaseNetconfSchema baseSchemaForCapabilities(NetconfSessionPreferences sessionPreferences);
}
