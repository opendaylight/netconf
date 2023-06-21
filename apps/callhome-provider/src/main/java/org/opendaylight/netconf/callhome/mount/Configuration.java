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
import java.io.Serial;
import java.util.Properties;

public class Configuration {
    public abstract static class ConfigurationException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = -7759423506815697761L;

        ConfigurationException(final String msg) {
            super(msg);
        }

        ConfigurationException(final String msg, final Exception cause) {
            super(msg, cause);
        }
    }

    public static class ReadException extends ConfigurationException {
        @Serial
        private static final long serialVersionUID = 1661483843463184121L;

        ReadException(final String msg, final Exception exc) {
            super(msg, exc);
        }
    }

    public static class MissingException extends ConfigurationException {
        @Serial
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

    private Properties properties;

    public Configuration() {
        properties = new Properties();
    }

    public Configuration(final String path) throws ConfigurationException {
        try {
            properties = readFromPath(path);
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
}
