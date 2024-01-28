/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collection;
import java.util.HashSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.binding.runtime.api.ModuleInfoSnapshot;
import org.opendaylight.mdsal.binding.runtime.spi.ModuleInfoSnapshotBuilder;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Candidate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.ConfirmedCommit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Startup;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Url;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Validate$F;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.WritableRunning;
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
@NonNullByDefault
public final class DefaultBaseNetconfSchemas implements BaseNetconfSchemas {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultBaseNetconfSchemas.class);
    private static final VarHandle VH = MethodHandles.arrayElementVarHandle(BaseSchema[].class);

    private final BaseSchema[] baseSchemas = new BaseSchema[2 ^ 9];
    private final YangParserFactory parserFactory;

    @Inject
    @Activate
    public DefaultBaseNetconfSchemas(@Reference final YangParserFactory parserFactory) throws YangParserException {
        this.parserFactory = requireNonNull(parserFactory);
    }

    @Override
    public BaseSchema baseSchemaForCapabilities(final Collection<String> capabilityURNs) {
        int offset = 0;
        final var candidate = capabilityURNs.contains(CapabilityURN.CANDIDATE);
        if (candidate) {
            offset |= 0x001;
        }
        final var confirmedCommit = capabilityURNs.contains(CapabilityURN.CONFIRMED_COMMIT_1_1)
            || capabilityURNs.contains(CapabilityURN.CONFIRMED_COMMIT);
        if (confirmedCommit) {
            offset |= 0x002;
        }
        final var startup = capabilityURNs.contains(CapabilityURN.STARTUP);
        if (startup) {
            offset |= 0x004;
        }
        final var url = capabilityURNs.contains(CapabilityURN.URL);
        if (url) {
            offset |= 0x008;
        }
        final var validate = capabilityURNs.contains(CapabilityURN.VALIDATE_1_1)
            || capabilityURNs.contains(CapabilityURN.VALIDATE);
        if (validate) {
            offset |= 0x010;
        }
        final var writableRunning = capabilityURNs.contains(CapabilityURN.WRITABLE_RUNNING);
        if (writableRunning) {
            offset |= 0x020;
        }
        final var notifications = capabilityURNs.contains(CapabilityURN.NOTIFICATION);
        if (notifications) {
            offset |= 0x040;
        }
        final var library = capabilityURNs.contains(CapabilityURN.YANG_LIBRARY)
            || capabilityURNs.contains(CapabilityURN.YANG_LIBRARY_1_1);
        if (library) {
            offset |= 0x080;
        }
        final var monitoring = capabilityURNs.contains(
            "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring?module=ietf-netconf-monitoring&revision=2010-10-04");
        if (monitoring) {
            offset |= 0x100;
        }

        final var existing = (BaseSchema) VH.getAcquire(baseSchemas, offset);
        return existing != null ? existing
            : loadBaseSchema(offset, candidate, confirmedCommit, startup, url, validate, writableRunning, notifications,
                library, monitoring);
    }

    private BaseSchema loadBaseSchema(final int offset, final boolean candidate, final boolean confirmedCommit,
            final boolean startup, final boolean url, final boolean validate, final boolean writableRunning,
            final boolean notifications, final boolean library, final boolean monitoring) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Loading base schema at offset {}", Integer.toHexString(offset));
        }

        final var builder = new ModuleInfoSnapshotBuilder(parserFactory)
            .add(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.$YangModuleInfoImpl
                .getInstance());

        final var netconfFeatures = new HashSet<YangFeature<?, IetfNetconfData>>();
        if (candidate) {
            netconfFeatures.add(Candidate.VALUE);
        }
        if (confirmedCommit) {
            netconfFeatures.add(ConfirmedCommit.VALUE);
        }
        if (startup) {
            netconfFeatures.add(Startup.VALUE);
        }
        if (url) {
            netconfFeatures.add(Url.VALUE);
        }
        if (validate) {
            netconfFeatures.add(Validate$F.VALUE);
        }
        if (writableRunning) {
            netconfFeatures.add(WritableRunning.VALUE);
        }
        builder.addModuleFeatures(IetfNetconfData.class, netconfFeatures);

        if (library) {
            builder.add(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104
                .$YangModuleInfoImpl.getInstance());
        }
        if (monitoring) {
            builder.add(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004
                .$YangModuleInfoImpl.getInstance());
        }
        if (notifications) {
            builder
                .add(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                    .$YangModuleInfoImpl.getInstance())
                .add(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206
                    .$YangModuleInfoImpl.getInstance());
        }

        final ModuleInfoSnapshot snapshot;
        try {
            snapshot = builder.build();
        } catch (YangParserException e) {
            throw new IllegalStateException("Unexpected failure", e);
        }

        final var created = new BaseSchema(snapshot.getEffectiveModelContext());
        final var witness = (BaseSchema) VH.compareAndExchangeRelease(baseSchemas, offset, null, created);
        return witness != null ? witness : created;
    }
}
