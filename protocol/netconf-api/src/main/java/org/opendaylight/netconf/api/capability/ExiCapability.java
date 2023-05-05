/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.CapabilityURN;

/**
 * A capability representing a EXI Capability, as defined in
 * <a href="https://datatracker.ietf.org/doc/html/draft-varga-netconf-exi-capability-02#section-3">
 * Efficient XML Interchange Capability for NETCONF, section 3.1</a>.
 */
public record ExiCapability(
        Integer compression,
        ExiSchemas schemas,
        @NonNull String urn) implements ParameterizedCapability {
    private static final @NonNull String COMPRESSION_PARAM = "compression";
    private static final @NonNull String SCHEMAS_PARAM = "schemas";
    private static final @NonNull Set<String> PARAMETERS = ImmutableSet.of(COMPRESSION_PARAM, SCHEMAS_PARAM);

    public ExiCapability(final Integer compression, final ExiSchemas schemas) {
        this(compression, schemas, buildUrn(compression, schemas));
    }

    private static String buildUrn(final Integer compression, final ExiSchemas schemas) {
        final var sb = new StringBuilder(CapabilityURN.EXI);
        boolean isFirstParam = true;
        if (compression != null) {
            sb.append("?").append(COMPRESSION_PARAM).append("=").append(compression);
            isFirstParam = false;
        }
        if (schemas != null) {
            sb.append(isFirstParam ? "?" : "&");
            sb.append(SCHEMAS_PARAM).append("=").append(schemas.getValue());
        }
        return sb.toString();
    }

    @Override
    public String urn() {
        return urn;
    }

    @Override
    public Set<String> parameterNames() {
        return PARAMETERS;
    }
}
