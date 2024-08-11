/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.monitoring;

import com.google.common.annotations.Beta;
import org.opendaylight.yangtools.concepts.Mutable;

/**
 * A {@link JavaCommonCounters} where each counter can be incremented.
 */
@Beta
public interface MutableJavaCommonCounters extends Mutable, JavaCommonCounters {

    void incInRpcs();

    void incInBadRpcs();

    void incOutRpcErrors();

    void incOutNotifications();
}
