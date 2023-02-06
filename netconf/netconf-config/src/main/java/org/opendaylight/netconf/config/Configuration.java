/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition
public @interface Configuration {
    @AttributeDefinition(name = "name-prefix")
    String namePrefix() default "remote-connector-processing-executor";
    @AttributeDefinition(name = "min-thread-count-flexible-thread-pool")
    int minThreadCountFlexibleThreadPool() default GlobalNetconfProcessingExecutor.DEFAULT_MIN_THREAD_COUNT;
    @AttributeDefinition(name = "max-thread-count-flexible-thread-pool")
    int maxThreadCountFlexibleThreadPool() default GlobalNetconfProcessingExecutor.DEFAULT_MAX_THREAD_COUNT;
    @AttributeDefinition(name = "keep-alive-millis-flexible-thread-pool")
    long keepAliveMillisFlexibleThreadPool() default GlobalNetconfProcessingExecutor.DEFAULT_KEEPALIVE_MILLIS;
    @AttributeDefinition(name = "max-thread-count-scheduled-thread-pool")
    int maxThreadCountScheduledThreadPool() default GlobalNetconfSshScheduledExecutor.DEFAULT_MAX_THREAD_COUNT;
}
