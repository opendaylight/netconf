/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record NetconfSessionPreferences(
        @NonNull ImmutableMap<String, CapabilityOrigin> nonModuleCaps,
        @NonNull ImmutableMap<QName, CapabilityOrigin> moduleBasedCaps,
        @Nullable SessionIdType sessionId) {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfSessionPreferences.class);
    private static final ParameterMatcher MODULE_PARAM = new ParameterMatcher("module=");
    private static final ParameterMatcher REVISION_PARAM = new ParameterMatcher("revision=");
    private static final ParameterMatcher BROKEN_REVISON_PARAM = new ParameterMatcher("amp;revision=");
    private static final Splitter AMP_SPLITTER = Splitter.on('&');

    public NetconfSessionPreferences {
        requireNonNull(nonModuleCaps);
        requireNonNull(moduleBasedCaps);
    }

    public static @NonNull NetconfSessionPreferences fromNetconfSession(final NetconfClientSession session) {
        return fromStrings(session.getServerCapabilities(), CapabilityOrigin.DeviceAdvertised,
           session.sessionId());
    }

    @VisibleForTesting
    public static @NonNull NetconfSessionPreferences fromStrings(final Collection<String> capabilities) {
        return fromStrings(capabilities, CapabilityOrigin.DeviceAdvertised, null);
    }

    public static @NonNull NetconfSessionPreferences fromStrings(final Collection<String> capabilities,
            final CapabilityOrigin capabilityOrigin, final SessionIdType sessionId) {
        final var moduleBasedCaps = new HashMap<QName, CapabilityOrigin>();
        final var nonModuleCaps = new HashMap<String, CapabilityOrigin>();

        for (final String capability : capabilities) {
            nonModuleCaps.put(capability, capabilityOrigin);
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
                addModuleQName(moduleBasedCaps, nonModuleCaps, capability, cachedQName(namespace, revision, moduleName),
                        capabilityOrigin);
                continue;
            }

            /*
             * We have seen devices which mis-escape revision, but the revision may not
             * even be there. First check if there is a substring that matches revision.
             */
            if (Iterables.any(queryParams, input -> input.contains("revision="))) {
                LOG.debug("Netconf device was not reporting revision correctly, trying to get amp;revision=");
                revision = BROKEN_REVISON_PARAM.from(queryParams);
                if (Strings.isNullOrEmpty(revision)) {
                    LOG.warn("Netconf device returned revision incorrectly escaped for {}, ignoring it", capability);
                    addModuleQName(moduleBasedCaps, nonModuleCaps, capability,
                            cachedQName(namespace, moduleName), capabilityOrigin);
                } else {
                    addModuleQName(moduleBasedCaps, nonModuleCaps, capability,
                            cachedQName(namespace, revision, moduleName), capabilityOrigin);
                }
                continue;
            }

            // Fallback, no revision provided for module
            addModuleQName(moduleBasedCaps, nonModuleCaps, capability,
                    cachedQName(namespace, moduleName), capabilityOrigin);
        }

        return new NetconfSessionPreferences(ImmutableMap.copyOf(nonModuleCaps), ImmutableMap.copyOf(moduleBasedCaps),
                sessionId);
    }

    public @Nullable CapabilityOrigin capabilityOrigin(final QName capability) {
        return moduleBasedCaps.get(requireNonNull(capability));
    }

    public @Nullable CapabilityOrigin capabilityOrigin(final String capability) {
        return nonModuleCaps.get(requireNonNull(capability));
    }

    // allows partial matches - assuming parameters are in the same order
    public boolean containsPartialNonModuleCapability(final String capability) {
        for (var nonModuleCap : nonModuleCaps.keySet()) {
            if (nonModuleCap.startsWith(capability)) {
                LOG.trace("capability {} partially matches {}", capability, nonModuleCaps);
                return true;
            }
        }
        return false;
    }

    public boolean containsNonModuleCapability(final String capability) {
        return nonModuleCaps.containsKey(capability);
    }

    public boolean containsModuleCapability(final QName capability) {
        return moduleBasedCaps.containsKey(capability);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("capabilities", nonModuleCaps)
            .add("moduleBasedCapabilities", moduleBasedCaps)
            .add("rollback", isRollbackSupported())
            .add("monitoring", isMonitoringSupported())
            .add("candidate", isCandidateSupported())
            .add("writableRunning", isRunningWritable())
            .toString();
    }

    public boolean isRollbackSupported() {
        return containsNonModuleCapability(CapabilityURN.ROLLBACK_ON_ERROR);
    }

    public boolean isCandidateSupported() {
        return containsNonModuleCapability(CapabilityURN.CANDIDATE);
    }

    public boolean isRunningWritable() {
        return containsNonModuleCapability(CapabilityURN.WRITABLE_RUNNING);
    }

    public boolean isNotificationsSupported() {
        return containsPartialNonModuleCapability(CapabilityURN.NOTIFICATION)
            || containsModuleCapability(NetconfMessageTransformUtil.IETF_NETCONF_NOTIFICATIONS);
    }

    public boolean isMonitoringSupported() {
        return containsModuleCapability(NetconfMessageTransformUtil.IETF_NETCONF_MONITORING)
            || containsPartialNonModuleCapability(
                NetconfMessageTransformUtil.IETF_NETCONF_MONITORING.getNamespace().toString());
    }

    /**
     * Merge module-based list of capabilities with current list of module-based capabilities.
     *
     * @param netconfSessionModuleCapabilities capabilities to merge into this
     * @return new instance of preferences with merged module-based capabilities
     */
    public NetconfSessionPreferences addModuleCaps(final NetconfSessionPreferences netconfSessionModuleCapabilities) {
        final var mergedCaps = Maps.<QName, CapabilityOrigin>newHashMapWithExpectedSize(moduleBasedCaps.size()
                + netconfSessionModuleCapabilities.moduleBasedCaps.size());
        mergedCaps.putAll(moduleBasedCaps);
        mergedCaps.putAll(netconfSessionModuleCapabilities.moduleBasedCaps);
        return new NetconfSessionPreferences(nonModuleCaps, ImmutableMap.copyOf(mergedCaps),
                netconfSessionModuleCapabilities.sessionId());
    }

    /**
     * Override current list of module-based capabilities.
     *
     * @param netconfSessionPreferences capabilities to override in this
     * @return new instance of preferences with replaced module-based capabilities
     */
    public NetconfSessionPreferences replaceModuleCaps(final NetconfSessionPreferences netconfSessionPreferences) {
        return new NetconfSessionPreferences(nonModuleCaps, netconfSessionPreferences.moduleBasedCaps,
                netconfSessionPreferences.sessionId());
    }

    public NetconfSessionPreferences replaceModuleCaps(final Map<QName, CapabilityOrigin> newModuleBasedCaps) {
        return new NetconfSessionPreferences(nonModuleCaps, ImmutableMap.copyOf(newModuleBasedCaps), sessionId());
    }

    /**
     * Merge list of non-module based capabilities with current list of non-module based capabilities.
     *
     * @param netconfSessionNonModuleCapabilities capabilities to merge into this
     * @return new instance of preferences with merged non-module based capabilities
     */
    public NetconfSessionPreferences addNonModuleCaps(
            final NetconfSessionPreferences netconfSessionNonModuleCapabilities) {
        final var mergedCaps = Maps.<String, CapabilityOrigin>newHashMapWithExpectedSize(
                nonModuleCaps.size() + netconfSessionNonModuleCapabilities.nonModuleCaps.size());
        mergedCaps.putAll(nonModuleCaps);
        mergedCaps.putAll(netconfSessionNonModuleCapabilities.nonModuleCaps);
        return new NetconfSessionPreferences(ImmutableMap.copyOf(mergedCaps), moduleBasedCaps,
                netconfSessionNonModuleCapabilities.sessionId());
    }

    /**
     * Override current list of non-module based capabilities.
     *
     * @param netconfSessionPreferences capabilities to override in this
     * @return new instance of preferences with replaced non-module based capabilities
     */
    public NetconfSessionPreferences replaceNonModuleCaps(final NetconfSessionPreferences netconfSessionPreferences) {
        return new NetconfSessionPreferences(netconfSessionPreferences.nonModuleCaps, moduleBasedCaps,
                netconfSessionPreferences.sessionId());
    }

    private static QName cachedQName(final String namespace, final String revision, final String moduleName) {
        return QName.create(namespace, revision, moduleName).intern();
    }

    private static QName cachedQName(final String namespace, final String moduleName) {
        return QName.create(XMLNamespace.of(namespace), moduleName).withoutRevision().intern();
    }

    private static void addModuleQName(final Map<QName, CapabilityOrigin> moduleBasedCaps,
                                       final Map<String, CapabilityOrigin> nonModuleCaps, final String capability,
                                       final QName qualifiedName, final CapabilityOrigin capabilityOrigin) {
        moduleBasedCaps.put(qualifiedName, capabilityOrigin);
        nonModuleCaps.remove(capability);
    }

    private static final class ParameterMatcher {
        private final Predicate<String> predicate;
        private final int skipLength;

        ParameterMatcher(final String name) {
            predicate = input -> input.startsWith(name);
            skipLength = name.length();
        }

        String from(final Iterable<String> params) {
            final var found = Iterables.tryFind(params, predicate);
            if (!found.isPresent()) {
                return null;
            }
            return found.get().substring(skipLength);
        }
    }
}
