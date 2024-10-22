/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import org.opendaylight.yangtools.concepts.Registration;

public record Subscription(long id) implements Registration {

    @Override
    public void close() {
        // Logic to terminate the subscription
    }
}
