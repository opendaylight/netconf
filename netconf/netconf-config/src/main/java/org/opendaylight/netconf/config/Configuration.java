/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.config;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@NonNullByDefault
@ObjectClassDefinition
public @interface Configuration {
    @AttributeDefinition(min = "1")
    String name$_$prefix() default GlobalNetconfThreadFactory.DEFAULT_NAME_PREFIX;
    @AttributeDefinition(min = "0")
    int min$_$thread$_$count$_$flexible$_$thread$_$pool()
        default GlobalNetconfProcessingExecutor.DEFAULT_MIN_THREAD_COUNT;
    @AttributeDefinition(min = "1")
    int max$_$thread$_$count$_$flexible$_$thread$_$pool()
        default GlobalNetconfProcessingExecutor.DEFAULT_MAX_THREAD_COUNT;
    @AttributeDefinition(min = "0")
    long keep$_$alive$_$millis$_$flexible$_$thread$_$pool()
        default GlobalNetconfProcessingExecutor.DEFAULT_KEEPALIVE_MILLIS;
}
