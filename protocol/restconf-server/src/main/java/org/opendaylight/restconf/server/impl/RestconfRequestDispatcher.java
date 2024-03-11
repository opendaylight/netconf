/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import static org.opendaylight.restconf.server.impl.NettyResponseUtils.handleException;
import static org.opendaylight.restconf.server.impl.NettyResponseUtils.setResponse;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netconf.transport.http.RequestDispatcher;
import org.opendaylight.restconf.nb.rfc8040.streams.RestconfStreamServletFactory;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Component(immediate = true, service = RequestDispatcher.class, property = "type=restconf")
public final class RestconfRequestDispatcher implements RequestDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfRequestDispatcher.class);
    private static final List<RequestMapping> REQUEST_MAPPINGS = List.of(RestconfRequestMapping::mapping);

    private final String uriBase;
    private final int uriBaseCutIndex;
    private final RestconfServer restconfServer;

    @Activate
    @Inject
    public RestconfRequestDispatcher(@Reference final RestconfServer restconfServer,
            @Reference final RestconfStreamServletFactory servletFactory) {
        this.restconfServer = restconfServer;
        uriBase = "/" + servletFactory.restconf();
        uriBaseCutIndex = uriBase.length();
    }

    @Override
    @SuppressWarnings("IllegalCatch")
    public void dispatch(final FullHttpRequest request, final FutureCallback<FullHttpResponse> callback) {

        LOG.debug("Dispatching {} {}", request.method(), request.uri());
        final var context = new DefaultContext(uriBase, request, callback);
        if (context.isValid()) {
            final var processor = REQUEST_MAPPINGS.stream()
                .map(mapping -> mapping.findMatching(context))
                .filter(matching -> matching != null).findFirst().orElse(null);
            if (processor != null) {
                try {
                    processor.process(restconfServer, context);
                    return;
                } catch  (RuntimeException e) {
                    handleException(e, request, callback);
                }
            }
        }
        setResponse(callback, request, HttpResponseStatus.NOT_FOUND);
    }
}
