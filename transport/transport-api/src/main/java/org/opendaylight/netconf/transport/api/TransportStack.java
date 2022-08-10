/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.api;

import java.util.concurrent.CompletionStage;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * A wiring of multiple transport components which provides resolution of {@link TransportChannel}s. There are generally
 * two ways to provide a stack:
 * <ul>
 *   <li>a listen stack, used for normal NETCONF servers and Call-Home clients, and</li>
 *   <li>a connect stack, used for normal NETCONF clients and Call-Home servers</li>
 * </ul>.
 */
@NonNullByDefault
public interface TransportStack {
    /**
     * Initiate shutdown of this stack, terminating all underlying transport sessions. Implementations of this method
     * are required to be idempotent, returning the same future.
     *
     * @return a {@link CompletionStage} which completes when all resources have been released.
     */
    CompletionStage<Empty> shutdown();
}
