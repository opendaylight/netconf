/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A capability representing a YANG module, as defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc6020#section-5.6.4">RFC6020, section 5.6.4</a>.
 */
// FIXME: this does not really follow the spec -- it does not expose feature/deviate parameters and it requires
//        schema content (i.e. YANG file)
public final class YangModuleCapability extends ParameterizedCapability {
    private static final @NonNull String MODULE_PARAM = "module";
    private static final @NonNull String REVISION_PARAM = "revision";
    private static final @NonNull String FEATURES_PARAM = "features";
    private static final @NonNull String DEVIATIONS_PARAM = "deviations";
    private static final @NonNull Set<String> PARAMETERS = Set.of(MODULE_PARAM, REVISION_PARAM, FEATURES_PARAM,
        DEVIATIONS_PARAM);

    private final @Nullable String revision;
    private final @Nullable String moduleName;
    private final @NonNull String moduleNamespace;
    private final @Nullable List<String> features;
    private final @Nullable List<String> deviations;
    private final @NonNull String urn;

    public YangModuleCapability(final String moduleNamespace, final @Nullable String moduleName,
            final @Nullable String revision, @Nullable final List<String> features,
            @Nullable final List<String> deviations) {
        this.moduleNamespace = requireNonNull(moduleNamespace);
        this.moduleName = moduleName;
        this.revision = revision;
        this.features = features;
        this.deviations = deviations;

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
        urn = sb.toString();
    }

    @Override
    public String urn() {
        return urn;
    }

    public Optional<String> getModuleName() {
        return Optional.of(moduleName);
    }

    public Optional<String> getModuleNamespace() {
        return Optional.of(moduleNamespace);
    }

    public Optional<String> getRevision() {
        return Optional.ofNullable(revision);
    }

    public Optional<List<String>> getFeatures() {
        return Optional.ofNullable(features);
    }

    public Optional<List<String>> getDeviations() {
        return Optional.ofNullable(deviations);
    }

    @Override
    public Set<String> parameterNames() {
        return PARAMETERS;
    }
}
