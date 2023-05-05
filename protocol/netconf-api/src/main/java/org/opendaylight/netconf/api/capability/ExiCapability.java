/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import com.google.common.collect.ImmutableSet;
import java.util.Objects;
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
    private static final @NonNull Set<String> PARAMETERS = ImmutableSet.of(COMPRESSION_PARAM, SCHEMAS_PARAM);

    private final @Nullable Integer compression;
    private final @Nullable Schemas schemas;
    private final @NonNull String urn;

    public ExiCapability(final @Nullable Integer compression, final @Nullable Schemas schemas) {
        this.compression = compression;
        this.schemas = schemas;

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
        urn = sb.toString();
    }

    public enum Schemas {
        BUILTIN("builtin"),
        BASE_1_1("base:1.1");

        private final String value;

        Schemas(final String value) {
            this.value = value;
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
        return urn;
    }

    @Override
    public Set<String> parameterNames() {
        return PARAMETERS;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ExiCapability that)) {
            return false;
        }
        return Objects.equals(compression, that.compression) && Objects.equals(schemas, that.schemas)
            && Objects.equals(urn, that.urn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(compression, schemas, urn);
    }
}
