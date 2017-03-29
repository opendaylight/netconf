/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool.xml.notification.files.streams;

import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.annotation.DomHandler;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContentHandler implements DomHandler<String, StreamResult> {

    private static final Logger LOG = LoggerFactory.getLogger(ContentHandler.class);

    private static final String CONTENT_START_TAG = "<content>";
    private static final String CONTENT_END_TAG = "</content>";

    private final StringWriter xmlWriter = new StringWriter();

    @Override
    public StreamResult createUnmarshaller(final ValidationEventHandler errorHandler) {
        xmlWriter.getBuffer().setLength(0);
        return new StreamResult(xmlWriter);
    }

    @Override
    public String getElement(final StreamResult rt) {
        final String xml = rt.getWriter().toString();
        final int beginIndex = xml.indexOf(CONTENT_START_TAG) + CONTENT_START_TAG.length();
        final int endIndex = xml.indexOf(CONTENT_END_TAG);
        return xml.substring(beginIndex, endIndex);
    }

    @Override
    public Source marshal(final String n, final ValidationEventHandler errorHandler) {
        try {
            final String xml = CONTENT_START_TAG + n.trim() + CONTENT_END_TAG;
            final StringReader xmlReader = new StringReader(xml);
            return new StreamSource(xmlReader);
        } catch (final Exception e) {
            LOG.error("Error reading and parsing stream xml", e);
            throw new IllegalStateException(e);
        }
    }

}
