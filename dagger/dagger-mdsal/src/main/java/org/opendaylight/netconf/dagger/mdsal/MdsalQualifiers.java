/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.mdsal;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;

/**
 * Namespace for Dagger Qualifiers used to distinguish between MD-SAL service injections.
 */
public final class MdsalQualifiers {

    private MdsalQualifiers() {
        // Hidden on purpose
    }

    @Qualifier
    @Retention(RUNTIME)
    public @interface SchemaServiceContext {}
}
