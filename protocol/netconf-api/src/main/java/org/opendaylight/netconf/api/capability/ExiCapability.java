/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.CapabilityURN;

/**
 * @author nite
 *
 */
public final class ExiCapability extends ParameterizedCapability {
    private static final @NonNull String COMPRESSION_PARAM = "compression";
    private static final @NonNull String SCHEMAS_PARAM = "schemas";

    private static final @NonNull Set<String> PARAMETERS = Set.of(COMPRESSION_PARAM, SCHEMAS_PARAM);

    @Override
    public String urn() {
        return CapabilityURN.EXI;
    }

    @Override
    public Set<String> parameterNames() {
        return PARAMETERS;
    }

}
