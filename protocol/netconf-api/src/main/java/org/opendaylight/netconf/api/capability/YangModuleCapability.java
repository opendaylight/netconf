/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;

/**
 * A capability representing a YANG module, as defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc6020#section-5.6.4">RFC6020, section 5.6.4</a>.
 */
public record YangModuleCapability(
        @NonNull String moduleNamespace,
        String moduleName,
        String revision,
        List<String> features,
        List<String> deviations,
        String urn) implements ParameterizedCapability {
    private static final @NonNull String MODULE_PARAM = "module";
    private static final @NonNull String REVISION_PARAM = "revision";
    private static final @NonNull String FEATURES_PARAM = "features";
    private static final @NonNull String DEVIATIONS_PARAM = "deviations";
    private static final @NonNull Set<String> PARAMETERS = ImmutableSet.of(MODULE_PARAM, REVISION_PARAM, FEATURES_PARAM,
        DEVIATIONS_PARAM);

    public YangModuleCapability(final @NonNull String moduleNamespace, final String moduleName,
            final String revision, final List<String> features, final List<String> deviations) {
        this(requireNonNull(moduleNamespace), moduleName, revision,
            features == null || features.isEmpty() ? null : List.copyOf(features),
            deviations == null || deviations.isEmpty() ? null : List.copyOf(deviations),
            buildUrn(moduleNamespace, moduleName, revision, features, deviations));
    }

    private static String buildUrn(final String moduleNamespace, final String moduleName, final String revision,
            final List<String> features, final List<String> deviations) {
        final var sb = new StringBuilder().append(moduleNamespace);
        boolean isFirstParam = true;
        if (moduleName != null) {
            sb.append("?").append(MODULE_PARAM).append("=").append(moduleName);
            isFirstParam = false;
        }
        if (revision != null) {
            sb.append(isFirstParam ? "?" : "&");
            sb.append(REVISION_PARAM).append("=").append(revision);
            isFirstParam = false;
        }
        if (features != null && !features.isEmpty()) {
            sb.append(isFirstParam ? "?" : "&");
            sb.append(FEATURES_PARAM).append("=").append(String.join(",", features));
            isFirstParam = false;
        }
        if (deviations != null && !deviations.isEmpty()) {
            sb.append(isFirstParam ? "?" : "&");
            sb.append(DEVIATIONS_PARAM).append("=").append(String.join(",", deviations));
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
