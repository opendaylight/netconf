/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import java.util.Set;

/**
 * A {@link Capability} which defines a set of URI query parameters.
 *
 */
public abstract sealed class ParameterizedCapability implements Capability
        permits YangModuleCapability, ExiCapability {

    public abstract Set<String> parameterNames();
}
