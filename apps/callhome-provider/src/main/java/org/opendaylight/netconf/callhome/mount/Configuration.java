/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.mount;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Configuration {
    public abstract static class ConfigurationException extends RuntimeException {
        private static final long serialVersionUID = -7759423506815697761L;

        ConfigurationException(final String msg) {
            super(msg);
        }

        ConfigurationException(final String msg, final Exception cause) {
            super(msg, cause);
        }
    }

    public static class ReadException extends ConfigurationException {
        private static final long serialVersionUID = 1661483843463184121L;

        ReadException(final String msg, final Exception exc) {
            super(msg, exc);
        }
    }

    public static class MissingException extends ConfigurationException {
        private static final long serialVersionUID = 3406998256398889038L;

        private final String key;

        MissingException(final String key) {
            super("Key not found: " + key);
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    public static class IllegalValueException extends ConfigurationException {
        private static final long serialVersionUID = -1172346869408302687L;

        private final String key;
        private final String value;

        IllegalValueException(final String key, final String value) {
            super("Key has an illegal value. Key: " + key + ", Value: " + value);
            this.key = key;
            this.value = value;
        }

        IllegalValueException(final String key, final String value, final Exception cause) {
            super("Key has an illegal value. Key: " + key + ", Value: " + value, cause);
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }
    }

    private Properties properties;

    public Configuration() {
        properties = new Properties();
    }

    public Configuration(final String path) throws ConfigurationException {
        try {
            this.properties = readFromPath(path);
        } catch (IOException ioe) {
            throw new ReadException(path, ioe);
        }
    }

    private Properties readFromPath(final String filePath) throws IOException {
        return readFromFile(new File(filePath));
    }

    private Properties readFromFile(final File file) throws IOException {
        FileInputStream stream = new FileInputStream(file);
        properties = readFrom(stream);
        return properties;
    }

    private static Properties readFrom(final InputStream stream) throws IOException {
        Properties properties = new Properties();
        properties.load(stream);
        return properties;
    }

    public void set(final String key, final String value) {
        properties.setProperty(key, value);
    }

    String get(final String key) {
        String result = (String) properties.get(key);
        if (result == null) {
            throw new MissingException(key);
        }
        return result;
    }

    public int getAsPort(final String key) {
        String keyValue = get(key);
        try {
            int newPort = Integer.parseInt(keyValue);
            if (newPort < 0 || newPort > 65535) {
                throw new IllegalValueException(key, keyValue);
            }
            return newPort;
        } catch (NumberFormatException e) {
            throw new IllegalValueException(key, keyValue, e);
        }
    }
}
