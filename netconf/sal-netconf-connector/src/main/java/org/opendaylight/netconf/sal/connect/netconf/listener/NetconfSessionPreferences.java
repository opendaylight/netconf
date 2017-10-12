/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.listener;

import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yangtools.yang.common.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfSessionPreferences {

    private static final class ParameterMatcher {
        private final Predicate<String> predicate;
        private final int skipLength;

        ParameterMatcher(final String name) {
            predicate = input -> input.startsWith(name);

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

    private static final Logger LOG = LoggerFactory.getLogger(NetconfSessionPreferences.class);
    private static final ParameterMatcher MODULE_PARAM = new ParameterMatcher("module=");
    private static final ParameterMatcher REVISION_PARAM = new ParameterMatcher("revision=");
    private static final ParameterMatcher BROKEN_REVISON_PARAM = new ParameterMatcher("amp;revision=");
    private static final Splitter AMP_SPLITTER = Splitter.on('&');
    private static final Predicate<String> CONTAINS_REVISION = input -> input.contains("revision=");

    private final Map<QName, CapabilityOrigin> moduleBasedCaps;
    private final Map<String, CapabilityOrigin> nonModuleCaps;

    NetconfSessionPreferences(final Map<String, CapabilityOrigin> nonModuleCaps,
                              final Map<QName, CapabilityOrigin> moduleBasedCaps) {
        this.nonModuleCaps = Preconditions.checkNotNull(nonModuleCaps);
        this.moduleBasedCaps = Preconditions.checkNotNull(moduleBasedCaps);
    }

    public Set<QName> getModuleBasedCaps() {
        return moduleBasedCaps.keySet();
    }

    public Map<QName, CapabilityOrigin> getModuleBasedCapsOrigin() {
        return moduleBasedCaps;
    }

    public Set<String> getNonModuleCaps() {
        return nonModuleCaps.keySet();
    }

    public Map<String, CapabilityOrigin> getNonModuleBasedCapsOrigin() {
        return nonModuleCaps;
    }

    // allows partial matches - assuming parameters are in the same order
    public boolean containsPartialNonModuleCapability(final String capability) {
        final Iterator<String> iterator = getNonModuleCaps().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().startsWith(capability)) {
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
        return containsNonModuleCapability(NetconfMessageTransformUtil.NETCONF_ROLLBACK_ON_ERROR_URI.toString());
    }

    public boolean isCandidateSupported() {
        return containsNonModuleCapability(NetconfMessageTransformUtil.NETCONF_CANDIDATE_URI.toString());
    }

    public boolean isRunningWritable() {
        return containsNonModuleCapability(NetconfMessageTransformUtil.NETCONF_RUNNING_WRITABLE_URI.toString());
    }

    public boolean isNotificationsSupported() {
        return containsPartialNonModuleCapability(NetconfMessageTransformUtil.NETCONF_NOTIFICATONS_URI.toString())
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
     *
     * @return new instance of preferences with merged module-based capabilities
     */
    public NetconfSessionPreferences addModuleCaps(final NetconfSessionPreferences netconfSessionModuleCapabilities) {
        final Map<QName, CapabilityOrigin> mergedCaps = Maps.newHashMapWithExpectedSize(moduleBasedCaps.size()
                + netconfSessionModuleCapabilities.getModuleBasedCaps().size());
        mergedCaps.putAll(moduleBasedCaps);
        mergedCaps.putAll(netconfSessionModuleCapabilities.getModuleBasedCapsOrigin());
        return new NetconfSessionPreferences(getNonModuleBasedCapsOrigin(), mergedCaps);
    }

    /**
     * Override current list of module-based capabilities.
     *
     * @param netconfSessionPreferences capabilities to override in this
     *
     * @return new instance of preferences with replaced module-based capabilities
     */
    public NetconfSessionPreferences replaceModuleCaps(final NetconfSessionPreferences netconfSessionPreferences) {
        return new NetconfSessionPreferences(
                getNonModuleBasedCapsOrigin(), netconfSessionPreferences.getModuleBasedCapsOrigin());
    }

    public NetconfSessionPreferences replaceModuleCaps(final Map<QName, CapabilityOrigin> newModuleBasedCaps) {
        return new NetconfSessionPreferences(getNonModuleBasedCapsOrigin(), newModuleBasedCaps);
    }


    /**
     * Merge list of non-module based capabilities with current list of non-module based capabilities.
     *
     * @param netconfSessionNonModuleCapabilities capabilities to merge into this
     *
     * @return new instance of preferences with merged non-module based capabilities
     */
    public NetconfSessionPreferences addNonModuleCaps(
            final NetconfSessionPreferences netconfSessionNonModuleCapabilities) {
        final Map<String, CapabilityOrigin> mergedCaps = Maps.newHashMapWithExpectedSize(
                nonModuleCaps.size() + netconfSessionNonModuleCapabilities.getNonModuleCaps().size());
        mergedCaps.putAll(getNonModuleBasedCapsOrigin());
        mergedCaps.putAll(netconfSessionNonModuleCapabilities.getNonModuleBasedCapsOrigin());
        return new NetconfSessionPreferences(mergedCaps, getModuleBasedCapsOrigin());
    }

    /**
     * Override current list of non-module based capabilities.
     *
     * @param netconfSessionPreferences capabilities to override in this
     *
     * @return new instance of preferences with replaced non-module based capabilities
     */
    public NetconfSessionPreferences replaceNonModuleCaps(final NetconfSessionPreferences netconfSessionPreferences) {
        return new NetconfSessionPreferences(
                netconfSessionPreferences.getNonModuleBasedCapsOrigin(), getModuleBasedCapsOrigin());
    }

    public static NetconfSessionPreferences fromNetconfSession(final NetconfClientSession session) {
        return fromStrings(session.getServerCapabilities());
    }

    private static QName cachedQName(final String namespace, final String revision, final String moduleName) {
        return QName.create(namespace, revision, moduleName).intern();
    }

    private static QName cachedQName(final String namespace, final String moduleName) {
        return QName.create(URI.create(namespace), moduleName).withoutRevision().intern();
    }

    public static NetconfSessionPreferences fromStrings(final Collection<String> capabilities) {
        // we do not know origin of capabilities from only Strings, so we set it to default value
        return fromStrings(capabilities, CapabilityOrigin.DeviceAdvertised);
    }

    public static NetconfSessionPreferences fromStrings(final Collection<String> capabilities,
                                                        final CapabilityOrigin capabilityOrigin) {
        final Map<QName, CapabilityOrigin> moduleBasedCaps = new HashMap<>();
        final Map<String, CapabilityOrigin> nonModuleCaps = new HashMap<>();

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
            if (Iterables.any(queryParams, CONTAINS_REVISION)) {

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

        return new NetconfSessionPreferences(ImmutableMap.copyOf(nonModuleCaps), ImmutableMap.copyOf(moduleBasedCaps));
    }

    private static void addModuleQName(final Map<QName, CapabilityOrigin> moduleBasedCaps,
                                       final Map<String, CapabilityOrigin> nonModuleCaps, final String capability,
                                       final QName qualifiedName, final CapabilityOrigin capabilityOrigin) {
        moduleBasedCaps.put(qualifiedName, capabilityOrigin);
        nonModuleCaps.remove(capability);
    }

    private final NetconfDeviceCapabilities capabilities = new NetconfDeviceCapabilities();

    public NetconfDeviceCapabilities getNetconfDeviceCapabilities() {
        return capabilities;
    }


}
