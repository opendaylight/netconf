/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.ModuleLike;

/**
 * Yang model representing capability.
 */
public final class YangModuleCapability implements Capability {
    private final @NonNull String urn;
    private final String content;
    private final String revision;
    private final String moduleName;
    private final String moduleNamespace;

    public YangModuleCapability(final ModuleLike module, final String content) {
        final var sb = new StringBuilder()
            .append(module.getNamespace()).append("?module=").append(module.getName());
        module.getRevision().ifPresent(rev -> sb.append("&revision=").append(rev));
        urn = sb.toString();
        this.content = requireNonNull(content);
        moduleName = module.getName();
        moduleNamespace = module.getNamespace().toString();
        revision = module.getRevision().map(Revision::toString).orElse(null);
    }

    @Override
    public String urn() {
        return urn;
    }

    public Optional<String> getCapabilitySchema() {
        return Optional.of(content);
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
}
