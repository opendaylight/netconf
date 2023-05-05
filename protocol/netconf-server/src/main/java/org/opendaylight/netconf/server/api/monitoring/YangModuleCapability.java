/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.monitoring;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Yang model representing capability.
 */
public final class YangModuleCapability extends BasicCapability {
    private final String moduleNamespace;
    private final String revision;
    private final String moduleName;
    private final String capabilitySchema;

    public YangModuleCapability(final String moduleNamespace,  final String moduleName, final @Nullable String revision,
            final @Nullable String capabilitySchema) {
        super(toCapabilityURI(moduleNamespace, moduleName, revision));
        this.moduleNamespace = requireNonNull(moduleNamespace);
        this.moduleName = requireNonNull(moduleName);
        this.revision = revision;
        this.capabilitySchema = capabilitySchema;
    }

    private static String toCapabilityURI(final String moduleNamespace, final String moduleName,
            final @Nullable String revision) {
        final var sb = new StringBuilder().append(moduleNamespace).append("?module=").append(moduleName);
        if (revision != null) {
            sb.append("&revision=").append(revision);
        }
        return sb.toString();
    }

    @Override
    public Optional<String> getCapabilitySchema() {
        return Optional.of(capabilitySchema);
    }

    @Override
    public Optional<String> getModuleName() {
        return Optional.of(moduleName);
    }

    @Override
    public Optional<String> getModuleNamespace() {
        return Optional.of(moduleNamespace);
    }

    @Override
    public Optional<String> getRevision() {
        return Optional.ofNullable(revision);
    }
}
