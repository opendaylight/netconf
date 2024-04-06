/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration of a typical {@link RestconfServer}.
 */
@ObjectClassDefinition
public @interface RestconfServerConfiguration {
    @AttributeDefinition(
        name = "default pretty-print",
        description = "Control the default value of the '" + PrettyPrintParam.uriName + "' query parameter.")
    boolean pretty$_$print() default false;
}