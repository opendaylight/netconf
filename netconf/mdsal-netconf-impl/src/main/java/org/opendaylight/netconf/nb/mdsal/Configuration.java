/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nb.mdsal;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Configuration of NETCONF northbound.
 */
@ObjectClassDefinition
public @interface Configuration {
    @AttributeDefinition(name = "connection-timeout-millis")
    long connectionTimeoutMillis() default 20000;
    @AttributeDefinition(name = "monitoring-update-interval")
    long monitorUpdateInterval() default 6;
}
