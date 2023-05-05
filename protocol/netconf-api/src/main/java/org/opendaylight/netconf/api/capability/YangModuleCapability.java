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
import org.eclipse.jdt.annotation.Nullable;

/**
 * A capability representing a YANG module, as defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc6020#section-5.6.4">RFC6020, section 5.6.4</a>.
 */
// FIXME: this does not really follow the spec -- it does not expose feature/deviate parameters and it requires
//        schema content (i.e. YANG file)
public final class YangModuleCapability extends ParameterizedCapability {
    private final @NonNull String uri;
    private final String content;
    // TODO: Revision
    private final String revision;
    // TODO: UnresolvedQName.Unqualified ?
    private final String moduleName;
    // TODO: XMLNamespace
    private final @NonNull String moduleNamespace;

    public YangModuleCapability(final String namespace, final @Nullable String module,
            final @Nullable String revision, final String content) {
        moduleNamespace = requireNonNull(namespace);
        moduleName = module;
        this.revision = revision;
        this.content = requireNonNull(content);

        final var sb = new StringBuilder().append(namespace);
        if (module != null) {
            sb.append("?module=").append(module);
        }
        if (revision != null) {
            sb.append("&revision=").append(revision);
        }
        uri = sb.toString();
    }

    @Override
    public String urn() {
        return moduleNamespace;
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
