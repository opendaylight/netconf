/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Settings {

    private static final Logger LOG = LoggerFactory.getLogger(Settings.class);
    private static final String KEYSTORE = "keystore";

    @Arg(dest = "notification-file")
    private File notificationFile;

    @Arg(dest = "rpc-file")
    private File rpcFile;

    @Arg(dest = "schema-dir")
    private File schemaDir;

    @Arg(dest = "port")
    private int port;

    @Arg(dest = "device-count")
    private int deviceCount;

    @Arg(dest = "debug")
    private boolean debug;

    @Arg(dest = "https")
    private boolean https;

    @Arg(dest = "password")
    private String password;

    @Arg(dest = KEYSTORE)
    private File keystore;

    @Arg(dest = "properties-path")
    private String propertiesPath;

    public File getNotificationFile() {
        return notificationFile;
    }

    public File getRpcFile() {
        return rpcFile;
    }

    public File getSchemaDir() {
        return schemaDir;
    }

    public int getPort() {
        return port;
    }

    public int getDeviceCount() {
        return deviceCount;
    }

    public boolean getDebugMode() {
        return debug;
    }

    public boolean getIsSecuredProtocol() {
        return https;
    }

    public File getKeystore() {
        return keystore;
    }

    public String getPassword() {
        return password;
    }

    public static Settings parseCmdLine(final String[] args) {
        final ArgumentParser parser = ArgumentParsers.newArgumentParser("restconf testtool");

        parser.description("restconf testtool");

        parser.addArgument("--notification-file")
                .type(File.class)
                .dest("notification-file");

        parser.addArgument("--rpc-file")
                .type(File.class)
                .dest("rpc-file");

        parser.addArgument("--schema-dir")
                .type(File.class)
                .dest("schema-dir");

        parser.addArgument("--port")
                .type(Integer.class)
                .setDefault(9999)
                .dest("port");

        parser.addArgument("--device-count")
                .type(Integer.class)
                .setDefault(1)
                .dest("device-count");

        parser.addArgument("--debug")
                .type(Boolean.class)
                .setDefault(false)
                .dest("debug");

        parser.addArgument("--https")
                .type(Boolean.class)
                .setDefault(false)
                .help("Protocol used for sending requests")
                .dest("https");

        parser.addArgument("--keystore")
                .type(File.class)
                .help("Path to a keystore file")
                .dest(KEYSTORE);

        parser.addArgument("--password")
                .type(String.class)
                .help("Password for keystore")
                .dest("password");

        parser.addArgument("--properties-path")
                .type(String.class)
                .help("File containing all the settings")
                .dest("properties-path");

        final Settings settings = new Settings();
        try {
            parser.parseArgs(args, settings);
            return settings;
        } catch (final ArgumentParserException e) {
            parser.handleError(e);
            throw new IllegalStateException("Couldn`t parse initial command line arguments", e);
        }
    }

    public void validate() {
        if (propertiesPath != null) {
            final Properties prop = new Properties();
            final InputStream inputStream;
            try {
                inputStream = new FileInputStream(propertiesPath);
                prop.load(inputStream);
                inputStream.close();
                loadProperties(prop);
            } catch (final IOException e) {
                throw new IllegalStateException("Failed to load properties", e);
            }
        }

        checkArgument(deviceCount > 0, "Device count has to be > 0");
        checkNotNull(schemaDir, "Schemas must be created");

        checkArgument(schemaDir.exists(), "Schemas dir has to exist");
        checkArgument(schemaDir.isDirectory(), "Schemas dir has to be a directory");
        checkArgument(schemaDir.canRead(), "Schemas dir has to be readable");

        if (https) {
            if (keystore != null) {
                checkArgument(keystore.exists(), "Keystore has to exist");
                checkArgument(keystore.isFile(), "Keystore path has to end with fileName");
                checkArgument(keystore.canRead(), "Keystore has to be readable");
                checkNotNull(password, "Missing password for keystore");
            } else {
                keystore = new File(Main.class.getResource("/keystore2.jks").toExternalForm());
                password = "atandt";
            }
        }
    }

    private void loadProperties(final Properties prop) {
        notificationFile = prop.getProperty("notificationFile") == null ? null : new File(prop.getProperty("notificationFile"));
        rpcFile = prop.getProperty("rpcFile") == null ? null : new File(prop.getProperty("rpcFile"));
        schemaDir = prop.getProperty("schemaDir") == null ? null : new File(prop.getProperty("schemaDir"));
        keystore = prop.getProperty(KEYSTORE) == null ? null : new File(prop.getProperty(KEYSTORE));
        port = Integer.parseInt(prop.getProperty("port", "9999"));
        deviceCount = Integer.parseInt(prop.getProperty("deviceCount", "1"));
        debug = Boolean.parseBoolean(prop.getProperty("debug", "true"));
        https = Boolean.parseBoolean(prop.getProperty("https", "false"));
        password = prop.getProperty("password", null);
    }

}
