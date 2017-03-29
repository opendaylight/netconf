/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool;

import ch.qos.logback.classic.Level;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.DispatcherType;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.opendaylight.restconfsb.mountpoint.schema.DirectorySchemaContextCache;
import org.opendaylight.restconfsb.testtool.datastore.Datastore;
import org.opendaylight.restconfsb.testtool.resources.Meta;
import org.opendaylight.restconfsb.testtool.resources.Restconf;
import org.opendaylight.restconfsb.testtool.xml.XmlReader;
import org.opendaylight.restconfsb.testtool.xml.XmlRpcReader;
import org.opendaylight.restconfsb.testtool.xml.XmlWriter;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final Pattern YANG_FILENAME_PATTERN = Pattern.compile("(?<name>.*)@(?<revision>\\d{4}-\\d{2}-\\d{2})\\.yang");

    private Main() {
        throw new UnsupportedOperationException("Main class");
    }

    public static void main(final String[] args) throws Exception {
        final Settings settings = Settings.parseCmdLine(args);
        settings.validate();

        final ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(settings.getDebugMode() ? Level.DEBUG : Level.INFO);
        final List<Server> server = new ArrayList<>();
        final SchemaContext schemaContext = createSchemaContext(settings.getSchemaDir());

        for (int i = 0; i < settings.getDeviceCount(); i++) {
            final Datastore datastore = new Datastore(schemaContext);
            final ResourceConfig config = new ResourceConfig();
            final int port = settings.getPort() + i;
            final Optional<File> notificationFile = Optional.fromNullable(settings.getNotificationFile());
            final Optional<File> rpcFile = Optional.fromNullable(settings.getRpcFile());
            final boolean isSecured = settings.getIsSecuredProtocol();
            config.registerInstances(new Restconf(datastore, notificationFile, rpcFile, port, isSecured),
                    new XmlWriter(schemaContext),
                    new XmlReader(schemaContext),
                    new XmlRpcReader(schemaContext),
                    new Meta());
            final ServletHolder jerseyServlet = new ServletHolder(new ServletContainer(config));
            if (isSecured) {
                server.add(new Server());
            } else {
                server.add(new Server(port));
            }
            final ServletContextHandler context = new ServletContextHandler(server.get(i), "/");
            context.addServlet(jerseyServlet, "/*");
            context.addFilter(RequestFilter.class, "/*", EnumSet.of(DispatcherType.INCLUDE, DispatcherType.REQUEST));

            if (isSecured) {
                // HTTPS configuration
                final HttpConfiguration https = new HttpConfiguration();
                https.addCustomizer(new SecureRequestCustomizer());

                // Configuring SSL
                final SslContextFactory sslContextFactory = new SslContextFactory();

                // Defining keystore path and passwords
                sslContextFactory.setKeyStorePath(settings.getKeystore().getPath());
                final String password = settings.getPassword();
                sslContextFactory.setKeyStorePassword(password);
                sslContextFactory.setKeyManagerPassword(password);

                // Configuring the connector
                final ServerConnector sslConnector = new ServerConnector(server.get(i), new SslConnectionFactory(sslContextFactory, "http/1.1"), new HttpConnectionFactory(https));
                sslConnector.setPort(port);

                // Setting HTTPS connectors
                server.get(i).setConnectors(new Connector[]{sslConnector});
            }
            server.get(i).start();
            LOG.info("Device {} started successfully.", port);
        }
    }

    private static SchemaContext createSchemaContext(final File cacheDir) {
        Preconditions.checkState(cacheDir.exists());
        Preconditions.checkState(cacheDir.isDirectory());
        final List<File> files = Arrays.asList(cacheDir.listFiles());
        final Collection<SourceIdentifier> requiredSources = new ArrayList<>();
        for (final File file : files) {
            final Matcher matcher = YANG_FILENAME_PATTERN.matcher(file.getName());
            if (matcher.matches()) {
                final SourceIdentifier identifier = new SourceIdentifier(matcher.group("name"), matcher.group("revision"));
                requiredSources.add(identifier);
                LOG.info("Loaded {}", identifier);
            } else {
                LOG.warn("File {} doesn't match expected yang file name pattern", file.getName());
            }
        }
        final DirectorySchemaContextCache cache = new DirectorySchemaContextCache(cacheDir.getPath());
        try {
            return cache.createSchemaContext(requiredSources);
        } catch (final SchemaResolutionException e) {
            throw new IllegalStateException(e);
        }
    }

}
