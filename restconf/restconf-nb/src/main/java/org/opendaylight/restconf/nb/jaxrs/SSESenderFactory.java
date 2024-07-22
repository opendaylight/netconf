/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static java.util.Objects.requireNonNull;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

/**
 * A factory for creating {@link SSESender}s.
 */
@Component(factory = SSESenderFactory.FACTORY_NAME, service = SSESenderFactory.class)
public final class SSESenderFactory implements AutoCloseable {
    public static final String FACTORY_NAME = "org.opendaylight.restconf.nb.jaxrs.SSESenderFactory";

    private static final String PROP_NAME_PREFIX = ".namePrefix";
    private static final String PROP_CORE_POOL_SIZE = ".corePoolSize";
    private static final String PROP_STREAMS_CONFIGURATION = ".streamsConfiguration";

    private final StreamsConfiguration configuration;
    private final DefaultPingExecutor pingExecutor;

    public SSESenderFactory(final StreamsConfiguration configuration, final String namePrefix, final int corePoolSize) {
        this.configuration = requireNonNull(configuration);
        pingExecutor = new DefaultPingExecutor(namePrefix, corePoolSize);
    }

    @Activate
    public SSESenderFactory(final Map<String, ?> props) {
        this((StreamsConfiguration) props.get(PROP_STREAMS_CONFIGURATION), (String) props.get(PROP_NAME_PREFIX),
            (int) requireNonNull(props.get(PROP_CORE_POOL_SIZE)));
    }

    @Override
    @Deactivate
    public void close() {
        pingExecutor.close();
    }

    void newSSESender(final SseEventSink sink, final Sse sse, final RestconfStream<?> stream,
            final EncodingName encodingName, final EventStreamGetParams getParams) {
        // FIXME: invert control here: we should call 'listener.addSession()', which in turn should call
        //        handler.init()/handler.close()
        final var handler = new SSESender(pingExecutor, sink, sse, stream, encodingName, getParams,
            configuration.maximumFragmentLength(), configuration.heartbeatInterval());

        try {
            handler.init();
        } catch (UnsupportedEncodingException e) {
            throw new NotFoundException("Unsupported encoding " + encodingName.name(), e);
        } catch (IllegalArgumentException | XPathExpressionException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    public static Map<String, ?> props(final StreamsConfiguration streamsConfiguration,
            final String namePrefix, final int corePoolSize) {
        return Map.of(
            PROP_STREAMS_CONFIGURATION, streamsConfiguration,
            PROP_NAME_PREFIX, namePrefix,
            PROP_CORE_POOL_SIZE, corePoolSize);
    }
}
