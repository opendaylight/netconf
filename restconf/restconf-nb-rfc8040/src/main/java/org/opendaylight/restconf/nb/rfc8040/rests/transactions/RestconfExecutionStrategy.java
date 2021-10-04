/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfDataService;

/**
 * A strategy for execution RESTCONF requests a particular target. There are two inherent types of strategies:
 * <ol>
 *   <li>local execution, which boils down to performing a set of operations on MD-SAL's DataBroker</li>
 *   <li>remote execution, which boils down to performing a set of operations on a DOMMountPoint, using whatever
 *       interfaces are available to it.</li>
 * </ol>
 *
 * <p>
 * Methods on this class are expected to mirror {@link RestconfDataService}, except the notable differences that:
 * <ul>
 *   <li>they are not tied to JAX-RS</li>
 *   <li>they do not map directly to RESTCONF endpoint layout</li>
 *   <li>their inputs are already validated by the caller</li>
 * </ul>
 *
 * <p>
 * One notable omission here is nested mountpoint execution. That is currently out of scope. In future this class needs
 * to evolve so that the shape of requests and request data allows for delegation of such requests -- i.e. every request
 * would undergo further distinction between local and remote one by the strategy implementation.
 */
public abstract class RestconfExecutionStrategy {

    // FIXME: for all this we will need to refactor ApiPath and all that jazz

}
