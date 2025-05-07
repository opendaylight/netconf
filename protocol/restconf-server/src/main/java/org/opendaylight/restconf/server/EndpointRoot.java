/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaders;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.EmptyResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResource;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceInstance;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceProvider;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.yangtools.concepts.AbstractObjectRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The root of resource hierarchy exposed from a particular endpoint.
 */
final class EndpointRoot {
    // split out to minimized retained fields
    private static final class ResourceReg extends AbstractObjectRegistration<WebHostResourceInstance> {
        private final ConcurrentHashMap<String, WebHostResource> map;

        @NonNullByDefault
        ResourceReg(final WebHostResourceInstance instance, final ConcurrentHashMap<String, WebHostResource> map) {
            super(instance);
            this.map = requireNonNull(map);
        }

        @Override
        protected void removeRegistration() {
            final var res = getInstance();
            final var path = res.path();
            if (map.remove(path, res)) {
                LOG.info("unregistered {} -> {}", path, res);
            } else {
                LOG.warn("unregister non existing {} -> {}, weird but harmless?", path, res);
            }
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(EndpointRoot.class);

    private final ConcurrentHashMap<String, WebHostResource> resources = new ConcurrentHashMap<>();
    private final PrincipalService principalService;
    // FIXME: at some point this should just be 'XRD xrd'
    private final WellKnownResources wellKnown;
    // FIXME: at some point this should be integrated into 'providers' Map with a coherent resource access
    //        API across the three classes of resources we have today
    private final Map<String, AbstractResource> fixedResources;

    @NonNullByDefault
    EndpointRoot(final PrincipalService principalService, final WellKnownResources wellKnown,
            final Map<String, AbstractResource> fixedResources) {
        this.principalService = requireNonNull(principalService);
        this.wellKnown = requireNonNull(wellKnown);
        this.fixedResources = requireNonNull(fixedResources);
    }

    @NonNullByDefault
    Registration registerProvider(final WebHostResourceProvider provider) {
        for (var path = provider.defaultPath(); ; path = provider.defaultPath() + "-" + UUID.randomUUID()) {
            @SuppressWarnings("resource")
            final var resource = provider.createInstance(path);
            final var prev = resources.putIfAbsent(path, resource);
            if (prev == null) {
                LOG.info("registered {} -> {}", path, resource);
                return new ResourceReg(resource, resources);
            }

            LOG.warn("{} -> {} conflicts with registered {}, retrying mapping", path, resource, prev);
            resource.close();
        }
    }

    @NonNullByDefault
    PreparedRequest prepare(final TransportSession session, final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers) {
        final var peeler = new SegmentPeeler(targetUri);
        if (!peeler.hasNext()) {
            // We only support OPTIONS
            return method == ImplementedMethod.OPTIONS ? AbstractResource.OPTIONS_ONLY_OK
                : AbstractResource.OPTIONS_ONLY_METHOD_NOT_ALLOWED;
        }

        final var segment = peeler.next();
        if (segment.equals(".well-known")) {
            return wellKnown.request(peeler, method, headers);
        }
        final var fixedResource = fixedResources.get(segment);
        if (fixedResource != null) {
            return fixedResource.prepare(peeler, session, method, targetUri, headers,
                principalService.acquirePrincipal(headers));
        }
        final var resource = resources.get(segment);
        return resource == null ? EmptyResponse.NOT_FOUND
            : resource.prepare(method, targetUri, headers, peeler, wellKnown);
    }
}
