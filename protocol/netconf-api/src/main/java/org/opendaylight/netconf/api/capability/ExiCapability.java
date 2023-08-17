/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.CapabilityURN;

/**
 * A capability representing a EXI Capability, as defined in
 * <a href="https://datatracker.ietf.org/doc/html/draft-varga-netconf-exi-capability-02#section-3">
 * Efficient XML Interchange Capability for NETCONF, section 3.1</a>.
 */
public final class ExiCapability extends ParameterizedCapability {
    private static final @NonNull String COMPRESSION_PARAM = "compression";
    private static final @NonNull String SCHEMAS_PARAM = "schemas";

    private static final @NonNull Set<String> PARAMETERS = Set.of(COMPRESSION_PARAM, SCHEMAS_PARAM);

    private final @Nullable Integer compression;
    private final @Nullable Schemas schemas;

    public ExiCapability(final @Nullable Integer compression, final @Nullable Schemas schemas) {
        this.compression = compression;
        this.schemas = schemas;
    }

    public enum Schemas {
        BUILTIN("builtin"),
        BASE_1_1("base:1.1");

        private final String value;

        Schemas(String value) {
            this.value = requireNonNull(value);
        }

        public String getValue() {
            return value;
        }
    }

    public Optional<Integer> getCompression() {
        return Optional.ofNullable(compression);
    }

    public Optional<Enum> getSchemas() {
        return Optional.ofNullable(schemas);
    }

    @Override
    public String urn() {
        final var sb = new StringBuilder(CapabilityURN.EXI);
        boolean isFirstParam = true;

        if (compression != null) {
            sb.append(isFirstParam ? "?" : "&");
            sb.append(COMPRESSION_PARAM).append("=").append(compression);
            isFirstParam = false;
        }
        if (schemas != null) {
            sb.append(isFirstParam ? "?" : "&");
            sb.append(SCHEMAS_PARAM).append("=").append(schemas.getValue());
        }
        return sb.toString();
    }

    @Override
    public Set<String> parameterNames() {
        return PARAMETERS;
    }
}
