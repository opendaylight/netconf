/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.runtime.spi.ModuleInfoSnapshotBuilder;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Candidate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.ConfirmedCommit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.RollbackOnError;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Startup;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Url;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Validate$F;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.WritableRunning;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Xpath;
import org.opendaylight.yangtools.yang.binding.YangFeature;
import org.opendaylight.yangtools.yang.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Singleton
public final class DefaultBaseNetconfSchemaProvider implements BaseNetconfSchemaProvider {
    private record Capabilities(
            boolean writableRunning,
            boolean candidate,
            boolean confirmedCommit,
            boolean rollbackOnError,
            boolean validate,
            boolean startup,
            boolean url,
            boolean xpath,
            boolean notifications,
            boolean library,
            boolean monitoring) {
        // Nothing else
    }

    private static final Logger LOG = LoggerFactory.getLogger(DefaultBaseNetconfSchemaProvider.class);

    private final LoadingCache<Capabilities, DefaultBaseNetconfSchema> baseSchemas = CacheBuilder.newBuilder()
        .weakValues().build(new CacheLoader<>() {
            @Override
            public DefaultBaseNetconfSchema load(final Capabilities key) throws YangParserException {
                LOG.debug("Loading base schema for {}", key);
                final var sw = Stopwatch.createStarted();

                final var builder = new ModuleInfoSnapshotBuilder(parserFactory)
                    .add(org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601
                        .YangModuleInfoImpl.getInstance());

                final var netconfFeatures = ImmutableSet.<YangFeature<?, @NonNull IetfNetconfData>>builder();
                if (key.writableRunning) {
                    netconfFeatures.add(WritableRunning.VALUE);
                }
                if (key.candidate) {
                    netconfFeatures.add(Candidate.VALUE);
                }
                if (key.confirmedCommit) {
                    netconfFeatures.add(ConfirmedCommit.VALUE);
                }
                if (key.rollbackOnError) {
                    netconfFeatures.add(RollbackOnError.VALUE);
                }
                if (key.validate) {
                    netconfFeatures.add(Validate$F.VALUE);
                }
                if (key.startup) {
                    netconfFeatures.add(Startup.VALUE);
                }
                if (key.url) {
                    netconfFeatures.add(Url.VALUE);
                }
                if (key.xpath) {
                    netconfFeatures.add(Xpath.VALUE);
                }
                builder.addModuleFeatures(IetfNetconfData.class, netconfFeatures.build());

                if (key.library) {
                    builder.add(org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104
                        .YangModuleInfoImpl.getInstance());
                }
                if (key.monitoring) {
                    builder.add(org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring
                        .rev101004.YangModuleInfoImpl.getInstance());
                }
                if (key.notifications) {
                    builder
                        .add(org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                            .YangModuleInfoImpl.getInstance())
                        .add(org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications
                            .rev120206.YangModuleInfoImpl.getInstance());
                }

                final var snapshot = builder.build();
                LOG.debug("Schema for {} assembled in {}", key, sw);
                return new DefaultBaseNetconfSchema(snapshot.modelContext());
            }
        });
    private final YangParserFactory parserFactory;

    @Inject
    @Activate
    public DefaultBaseNetconfSchemaProvider(@Reference final YangParserFactory parserFactory) {
        this.parserFactory = requireNonNull(parserFactory);
    }

    @Override
    public DefaultBaseNetconfSchema baseSchemaForCapabilities(final NetconfSessionPreferences sessionPreferences) {
        return baseSchemas.getUnchecked(new Capabilities(
            sessionPreferences.isRunningWritable(),
            sessionPreferences.isCandidateSupported(),
            sessionPreferences.containsNonModuleCapability(CapabilityURN.CONFIRMED_COMMIT_1_1)
                || sessionPreferences.containsNonModuleCapability(CapabilityURN.CONFIRMED_COMMIT),
            sessionPreferences.isRollbackSupported(),
            sessionPreferences.containsNonModuleCapability(CapabilityURN.VALIDATE_1_1)
                || sessionPreferences.containsNonModuleCapability(CapabilityURN.VALIDATE),
            sessionPreferences.containsNonModuleCapability(CapabilityURN.STARTUP),
            sessionPreferences.containsNonModuleCapability(CapabilityURN.URL),
            sessionPreferences.containsNonModuleCapability(CapabilityURN.XPATH),
            sessionPreferences.isNotificationsSupported(),
            sessionPreferences.containsNonModuleCapability(CapabilityURN.YANG_LIBRARY)
                || sessionPreferences.containsNonModuleCapability(CapabilityURN.YANG_LIBRARY_1_1),
            sessionPreferences.isMonitoringSupported()));
    }
}
