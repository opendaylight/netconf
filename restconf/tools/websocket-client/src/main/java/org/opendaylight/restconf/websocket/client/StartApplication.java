/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.websocket.client;

import com.google.common.collect.Sets;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
import org.apache.log4j.BasicConfigurator;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.api.AuthenticationStore;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.opendaylight.controller.config.threadpool.util.NamingThreadPoolFactory;
import org.opendaylight.controller.config.threadpool.util.ScheduledThreadPoolWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application starting point which is responsible for reading of input application arguments and creation of right
 * client handlers according to the used scheme.
 */
public final class StartApplication {

    private static final Logger LOG = LoggerFactory.getLogger(StartApplication.class);

    private static final String WS_SCHEME = "ws";
    private static final String WSS_SCHEME = "wss";
    private static final String THREAD_POOL_NAME = "websockets";

    private StartApplication() {
    }

    public static void main(String[] args) {
        BasicConfigurator.configure();
        final Optional<ApplicationSettings> applicationSettings = ApplicationSettings.parseApplicationSettings(args);
        if (applicationSettings.isPresent()) {
            final SslContextFactory sslContextFactory = getSslContextFactory(applicationSettings.get());
            final ThreadFactory threadFactory = new NamingThreadPoolFactory(THREAD_POOL_NAME);
            final ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolWrapper(
                    applicationSettings.get().getThreadPoolSize(), threadFactory).getExecutor();
            final List<WebSocketClientHandler> clientHandlers = applicationSettings.get().getStreams().stream()
                    .map(streamName -> getWebSocketClientHandler(applicationSettings.get(), sslContextFactory,
                            scheduledExecutorService, streamName))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            printHandledStreamsOverview(applicationSettings.get().getStreams(), clientHandlers);
            startAndLockOnClientHandlers(scheduledExecutorService, clientHandlers);
        }
        System.exit(0);
    }

    /**
     * Starts threads of all successfully created web-socket client handlers and waits until they finishes their work
     * (regular closing of the web-socket session or unexpected exception).
     *
     * @param scheduledExecutorService Executor for the web-socket client threads.
     * @param clientHandlers           List of created web-socket client handlers.
     */
    private static void startAndLockOnClientHandlers(final ScheduledExecutorService scheduledExecutorService,
            final List<WebSocketClientHandler> clientHandlers) {
        if (clientHandlers.isEmpty()) {
            LOG.info("There aren't any usable web-socket client handlers - shutting down of the application.");
        } else {
            final List<? extends Future<?>> startedThreads = clientHandlers.stream()
                    .map(scheduledExecutorService::submit)
                    .collect(Collectors.toList());
            LOG.info("Threads for all successfully created web-socket clients have been started.");
            for (final Future<?> future : startedThreads) {
                try {
                    future.get();
                } catch (final ExecutionException | InterruptedException e) {
                    LOG.warn("One of the web-socket handlers ends with unexpected exception.", e);
                }
            }
            LOG.info("All web-socket client threads have been closed - shutting down of the application.");
        }
    }

    /**
     * Prints information about successfully and unsuccessfully handled streams.
     *
     * @param streams        Input URIs of web-socket streams that were tended to be handled.
     * @param clientHandlers Successfully crafted web-socket client handlers.
     */
    private static void printHandledStreamsOverview(final List<String> streams,
            final List<WebSocketClientHandler> clientHandlers) {
        final Set<String> successfullyHandledStreams = clientHandlers.stream()
                .map(WebSocketClientHandler::getUri)
                .collect(Collectors.toSet());
        final Set<String> unsuccessfullyHandledStreams = Sets.difference(new HashSet<>(streams),
                successfullyHandledStreams);
        if (!successfullyHandledStreams.isEmpty()) {
            LOG.info("Successfully created stream handlers ({}): {}.", successfullyHandledStreams.size(),
                    successfullyHandledStreams);
        }
        if (!unsuccessfullyHandledStreams.isEmpty()) {
            LOG.warn("Unsuccessfully handled streams ({}): {}.", unsuccessfullyHandledStreams.size(),
                    unsuccessfullyHandledStreams);
        }
    }

    /**
     * Deriving of web-socket client handler using URI schema (currently WS and WSS are supported).
     *
     * @param applicationSettings      Application settings.
     * @param sslContextFactory        SSL context factory that us utilized if the WSS schema is used.
     * @param scheduledExecutorService Executor for launching of ping processes if it is necessary.
     * @param streamName               Input URI that holds stream name and starts with specified WS or WSS schema.
     * @return Instance of {@link WebSocketClientHandler} wrapped in {@link Optional} or {@link Optional#empty()} if
     *     the web-socket client handler cannot be created.
     */
    private static Optional<WebSocketClientHandler> getWebSocketClientHandler(
            final ApplicationSettings applicationSettings, final SslContextFactory sslContextFactory,
            final ScheduledExecutorService scheduledExecutorService, final String streamName) {
        try {
            final URI uri = URI.create(streamName);
            if (uri.getScheme() == null) {
                LOG.warn("Schema seems to be undefined in input URI {}. The web-socket client cannot be created "
                        + "for this stream.", uri);
            } else {
                HttpClient httpClient;
                switch (uri.getScheme().toLowerCase(Locale.getDefault())) {
                    case WS_SCHEME:
                        httpClient = new HttpClient();
                        break;
                    case WSS_SCHEME:
                        httpClient = new HttpClient(sslContextFactory);
                        break;
                    default:
                        LOG.warn("Unknown schema {} in input URI {}. The web-socket client cannot be created "
                                + "for this stream.", uri.getScheme(), uri);
                        return Optional.empty();
                }
                if (applicationSettings.getUsername() != null) {
                    final AuthenticationStore authenticationStore = httpClient.getAuthenticationStore();
                    authenticationStore.addAuthentication(new BasicAuthentication(uri, Authentication.ANY_REALM,
                            applicationSettings.getUsername(), applicationSettings.getPassword()));
                }
                return Optional.of(new WebSocketClientHandler(uri, applicationSettings.getPingInterval(),
                        applicationSettings.getPingMessage(), scheduledExecutorService, httpClient));
            }
        } catch (final IllegalArgumentException e) {
            LOG.warn("Stream {} cannot be parsed to URI. The web-socket client won't be created.", streamName);
        }
        return Optional.empty();
    }

    /**
     * Building of {@link org.eclipse.jetty.util.ssl.SslContextFactory} using input settings.
     *
     * @param applicationSettings User-defined settings.
     * @return Instance of {@link org.eclipse.jetty.util.ssl.SslContextFactory}.
     */
    private static SslContextFactory getSslContextFactory(final ApplicationSettings applicationSettings) {
        final SslContextFactory sslContextFactory = new SslContextFactory();
        if (applicationSettings.getKeystorePath() != null) {
            final Resource keyStoreResource = Resource.newResource(applicationSettings.getKeystorePath());
            sslContextFactory.setKeyStoreResource(keyStoreResource);
            sslContextFactory.setKeyStorePassword(applicationSettings.getKeystorePassword());
        }
        if (applicationSettings.getTruststorePath() != null) {
            final Resource truststoreResource = Resource.newResource(applicationSettings.getTruststorePath());
            sslContextFactory.setTrustStoreResource(truststoreResource);
            sslContextFactory.setTrustStorePassword(applicationSettings.getTruststorePassword());
        }
        sslContextFactory.setTrustAll(applicationSettings.isTrustAll());
        sslContextFactory.setExcludeProtocols(applicationSettings.getExcludedProtocols().toArray(new String[0]));
        sslContextFactory.setExcludeCipherSuites(applicationSettings.getExcludedCipherSuites().toArray(new String[0]));
        sslContextFactory.setIncludeCipherSuites(applicationSettings.getIncludedCipherSuites().toArray(new String[0]));
        sslContextFactory.setIncludeProtocols(applicationSettings.getIncludedProtocols().toArray(new String[0]));
        sslContextFactory.setRenegotiationAllowed(applicationSettings.isRegenerationAllowed());
        return sslContextFactory;
    }
}