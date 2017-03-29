/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint;

import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module.CapabilityOrigin;
import org.opendaylight.yangtools.yang.common.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RestconfPreferences {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfPreferences.class);
    private static final ParameterMatcher MODULE_PARAM = new ParameterMatcher("module=");
    private static final ParameterMatcher REVISION_PARAM = new ParameterMatcher("revision=");
    private static final ParameterMatcher BROKEN_REVISON_PARAM = new ParameterMatcher("amp;revision=");
    private static final Splitter AMP_SPLITTER = Splitter.on('&');
    private static final Predicate<String> CONTAINS_REVISION = new Predicate<String>() {
        @Override
        public boolean apply(final String input) {
            return input.contains("revision=");
        }
    };
    private final Map<QName, CapabilityOrigin> moduleBasedCaps;

    RestconfPreferences(final Map<QName, CapabilityOrigin> moduleBasedCaps) {
        this.moduleBasedCaps = Preconditions.checkNotNull(moduleBasedCaps);
    }

    private static QName cachedQName(final String namespace, final String revision, final String moduleName) {
        return QName.create(namespace, revision, moduleName).intern();
    }

    private static QName cachedQName(final String namespace, final String moduleName) {
        return QName.create(URI.create(namespace), null, moduleName).withoutRevision().intern();
    }

    public static RestconfPreferences fromStrings(final Collection<String> capabilities, final CapabilityOrigin capabilityOrigin) {
        final Map<QName, CapabilityOrigin> moduleBasedCaps = new HashMap<>();

        for (final String capability : capabilities) {
            final int qmark = capability.indexOf('?');
            if (qmark == -1) {
                continue;
            }

            final String namespace = capability.substring(0, qmark);
            final Iterable<String> queryParams = AMP_SPLITTER.split(capability.substring(qmark + 1));
            final String moduleName = MODULE_PARAM.from(queryParams);
            if (Strings.isNullOrEmpty(moduleName)) {
                continue;
            }

            String revision = REVISION_PARAM.from(queryParams);
            if (!Strings.isNullOrEmpty(revision)) {
                addModuleQName(moduleBasedCaps, cachedQName(namespace, revision, moduleName), capabilityOrigin);
                continue;
            }

            /*
             * We have seen devices which mis-escape revision, but the revision may not
             * even be there. First check if there is a substring that matches revision.
             */
            if (Iterables.any(queryParams, CONTAINS_REVISION)) {

                LOG.debug("Restconf device was not reporting revision correctly, trying to get amp;revision=");
                revision = BROKEN_REVISON_PARAM.from(queryParams);
                if (Strings.isNullOrEmpty(revision)) {
                    LOG.warn("Restconf device returned revision incorrectly escaped for {}, ignoring it", capability);
                    addModuleQName(moduleBasedCaps, cachedQName(namespace, moduleName), capabilityOrigin);
                } else {
                    addModuleQName(moduleBasedCaps, cachedQName(namespace, revision, moduleName), capabilityOrigin);
                }
                continue;
            }
            // Fallback, no revision provided for module
            addModuleQName(moduleBasedCaps, cachedQName(namespace, moduleName), capabilityOrigin);
        }
        return new RestconfPreferences(ImmutableMap.copyOf(moduleBasedCaps));
    }

    private static void addModuleQName(final Map<QName, CapabilityOrigin> moduleBasedCaps, final QName qName, final CapabilityOrigin capabilityOrigin) {
        moduleBasedCaps.put(qName, capabilityOrigin);
    }

    public Set<QName> getModuleBasedCaps() {
        return moduleBasedCaps.keySet();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("moduleBasedCapabilities", moduleBasedCaps)
                .toString();
    }

    private static final class ParameterMatcher {
        private final Predicate<String> predicate;
        private final int skipLength;

        ParameterMatcher(final String name) {
            predicate = new Predicate<String>() {
                @Override
                public boolean apply(final String input) {
                    return input.startsWith(name);
                }
            };

            this.skipLength = name.length();
        }

        private String from(final Iterable<String> params) {
            final Optional<String> o = Iterables.tryFind(params, predicate);
            if (!o.isPresent()) {
                return null;
            }

            return o.get().substring(skipLength);
        }
    }
}
