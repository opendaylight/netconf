/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.websocket.client;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.helper.HelpScreenException;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holder of the parsed user-input application arguments.
 */
final class ApplicationSettings {

    /**
     * Credentials used for basic authentication - grouping of username and password.
     */
    static final class Credentials {
        final String userName;
        final String password;

        private Credentials(final String userName, final String password) {
            this.userName = userName;
            this.password = password;
        }

        private static Credentials extractCredentials(final String basicAuthentication) {
            final String[] credentials = basicAuthentication.split(":");
            Preconditions.checkArgument(credentials.length == 2, "Both username and password must be specified in the "
                    + "format [username]:[password] for basic authentication.");
            final String userName = credentials[0].trim();
            final String password = credentials[1].trim();
            return new Credentials(userName, password);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationSettings.class);
    private static final ArgumentParser PARSER = ArgumentParsers.newFor("web-socket client test-tool").build();

    static {
        PARSER.addArgument("-s")
                .dest("streams")
                .required(true)
                .nargs("+")
                .help("Web-socket stream paths with ws or wss schemas.")
                .metavar("STREAM")
                .type(String.class);
        PARSER.addArgument("-pi")
                .dest("pingInterval")
                .help("Interval in milliseconds between sending of ping web-socket frames to server. "
                        + "Value of 0 disables ping process.")
                .metavar("INTERVAL")
                .setDefault(30000)
                .type(Integer.class);
        PARSER.addArgument("-pm")
                .dest("pingMessage")
                .help("Explicitly set ping message.")
                .metavar("MESSAGE")
                .setDefault("ping")
                .type(String.class);
        PARSER.addArgument("-t")
                .dest("threads")
                .help("Explicitly set size of thread-pool used for holding of web-socket handlers and ping processes.")
                .metavar("SIZE")
                .setDefault(4)
                .type(Integer.class);
        PARSER.addArgument("-r")
                .dest("regeneration")
                .help("Allowed TLS/SSL session regeneration.")
                .metavar("ALLOWED")
                .setDefault(false)
                .type(Boolean.class);
        PARSER.addArgument("-kpath")
                .dest("keystorePath")
                .help("Path to the certificates key-store file.")
                .metavar("PATH")
                .type(File.class);
        PARSER.addArgument("-kpass")
                .dest("keystorePassword")
                .help("Password used for unlocking of the certificates keystore.")
                .metavar("SECRET")
                .type(String.class);
        PARSER.addArgument("-tpath")
                .dest("truststorePath")
                .help("Path to the certificates trust-store file.")
                .metavar("PATH")
                .type(File.class);
        PARSER.addArgument("-tpass")
                .dest("truststorePassword")
                .help("Password used for unlocking of the certificates truststore.")
                .metavar("SECRET")
                .type(String.class);
        PARSER.addArgument("-ta")
                .dest("trustAll")
                .help("All incoming certificates are trusted when both truststore and keystore are not specified.")
                .metavar("TRUST")
                .setDefault(false)
                .type(Boolean.class);
        PARSER.addArgument("-ip")
                .dest("includedProtocols")
                .nargs("+")
                .help("Explicitly specified list of permitted versions of web-security protocols.")
                .metavar("PROTOCOL")
                .setDefault("TLSv1.2", "TLSv1.3")
                .type(String.class);
        PARSER.addArgument("-ep")
                .dest("excludedProtocols")
                .nargs("*")
                .help("Explicitly specified list of denied versions of web-security protocols (denied protocols have "
                        + "the highest priority).")
                .metavar("PROTOCOL")
                .setDefault("TLSv1", "TLSv1.1", "SSL", "SSLv2", "SSLv2Hello", "SSLv3")
                .type(String.class);
        PARSER.addArgument("-ic")
                .dest("includedCipherSuites")
                .nargs("+")
                .help("Explicitly specified list of permitted cipher suites.")
                .metavar("CIPHER")
                .setDefault("TLS_ECDHE.*", "TLS_DHE_RSA.*")
                .type(String.class);
        PARSER.addArgument("-ec")
                .dest("excludedCipherSuites")
                .nargs("*")
                .help("Explicitly specified list of denied cipher suites (denied ciphers have the highest priority).")
                .metavar("CIPHER")
                .setDefault(".*MD5.*", ".*RC4.*", ".*DSS.*", ".*NULL.*", ".*DES.*")
                .type(String.class);
        PARSER.addArgument("-b")
                .dest("basicAuthentication")
                .help("[username:password] used with basic authentication that can be required on upgrade-request.")
                .metavar("[USERNAME]:[PASSWORD]")
                .type(String.class);
    }

    @Arg(dest = "streams")
    private List<String> streams;
    @Arg(dest = "pingInterval")
    private int pingInterval;
    @Arg(dest = "pingMessage")
    private String pingMessage;
    @Arg(dest = "threads")
    private int threadPoolSize;
    @Arg(dest = "regeneration")
    private boolean regenerationAllowed;
    @Arg(dest = "keystorePath")
    private File keystorePath;
    @Arg(dest = "keystorePassword")
    private String keystorePassword;
    @Arg(dest = "truststorePath")
    private File truststorePath;
    @Arg(dest = "truststorePassword")
    private String truststorePassword;
    @Arg(dest = "trustAll")
    private boolean trustAll;
    @Arg(dest = "includedProtocols")
    private List<String> includedProtocols;
    @Arg(dest = "excludedProtocols")
    private List<String> excludedProtocols;
    @Arg(dest = "includedCipherSuites")
    private List<String> includedCipherSuites;
    @Arg(dest = "excludedCipherSuites")
    private List<String> excludedCipherSuites;
    @Arg(dest = "basicAuthentication")
    private String basicAuthentication;

    private Credentials credentials;

    private ApplicationSettings() {
    }

    /**
     * Creation of application settings object using input command-line arguments (factory method).
     *
     * @param arguments Raw program arguments.
     * @return Parsed arguments wrapped in {@link Optional} or {@link Optional#empty()} if only help is going
     *     to be invoked.
     */
    static Optional<ApplicationSettings> parseApplicationSettings(final String[] arguments) {
        final ApplicationSettings applicationSettings = new ApplicationSettings();
        try {
            PARSER.parseArgs(arguments, applicationSettings);
            applicationSettings.verifyParsedArguments();
            applicationSettings.credentials = Credentials.extractCredentials(applicationSettings.basicAuthentication);
        } catch (final ArgumentParserException | IllegalArgumentException e) {
            if (e instanceof HelpScreenException) {
                return Optional.empty();
            } else {
                final StringWriter helpWriter = new StringWriter();
                final PrintWriter helpPrintWriter = new PrintWriter(helpWriter);
                PARSER.printHelp(helpPrintWriter);
                LOG.error("Cannot parse input arguments {}.", arguments, e);
                LOG.info("Help: {}", helpWriter.toString());
                throw new IllegalArgumentException("Cannot parse input arguments", e);
            }
        }
        LOG.info("Application settings {} have been parsed successfully.", (Object) arguments);
        return Optional.of(applicationSettings);
    }

    private void verifyParsedArguments() {
        Preconditions.checkArgument(pingInterval >= 0, "Ping interval must be set to value higher than 0 (enabled) or "
                + "to 0 (disabled).");
        Preconditions.checkArgument(threadPoolSize > 0, "Thread pool must have capacity of at least 1 thread.");
        Preconditions.checkArgument((keystorePath == null && keystorePassword == null) || (keystorePath != null
                && keystorePassword != null), "Both keystore path and keystore password must be configured at once.");
        Preconditions.checkArgument((truststorePath == null && truststorePassword == null) || (truststorePath != null
                && truststorePassword != null), "Both truststore path and truststore password must be configured");
    }

    List<String> getStreams() {
        return new ArrayList<>(streams);
    }

    int getPingInterval() {
        return pingInterval;
    }

    String getPingMessage() {
        return pingMessage;
    }

    File getKeystorePath() {
        return keystorePath;
    }

    String getKeystorePassword() {
        return keystorePassword;
    }

    File getTruststorePath() {
        return truststorePath;
    }

    String getTruststorePassword() {
        return truststorePassword;
    }

    List<String> getIncludedProtocols() {
        return new ArrayList<>(includedProtocols);
    }

    List<String> getExcludedProtocols() {
        return new ArrayList<>(excludedProtocols);
    }

    List<String> getIncludedCipherSuites() {
        return new ArrayList<>(includedCipherSuites);
    }

    List<String> getExcludedCipherSuites() {
        return new ArrayList<>(excludedCipherSuites);
    }

    boolean isTrustAll() {
        return trustAll;
    }

    boolean isRegenerationAllowed() {
        return regenerationAllowed;
    }

    int getThreadPoolSize() {
        return threadPoolSize;
    }

    Credentials getCredentials() {
        return credentials;
    }
}