/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool.xml.notification;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import org.opendaylight.restconfsb.testtool.xml.notification.files.streams.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestconfNotifications {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfNotifications.class);

    private final Streams str;

    public RestconfNotifications(final File file) {
        this.str = mapXml(file);
    }

    /**
     * Gets the value of the streams property.
     *
     * @return possible object is
     * {@link Streams }
     */
    public Streams getStreams() {
        return this.str;
    }

    private Streams mapXml(final File file) {
        InputStream is = null;

        try {
            is = new FileInputStream(file);
        } catch (final FileNotFoundException e) {
            LOG.error("File not found", e);
        }

        try {
            final JAXBContext jc = JAXBContext.newInstance(Streams.class);
            final Unmarshaller u = jc.createUnmarshaller();
            return (Streams) u.unmarshal(is);
        } catch (final JAXBException e) {
            LOG.error("Error processing data", e);
            return null;
        }
    }

}
