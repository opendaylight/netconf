/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.io.StringReader;
import java.time.Instant;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Features of query parameters part of both notifications.
 */
abstract class AbstractQueryParams extends AbstractNotificationsData {
    // FIXME: BUG-7956: switch to using UntrustedXML
    private static final DocumentBuilderFactory DBF;

    static {
        final DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setCoalescing(true);
        f.setExpandEntityReferences(false);
        f.setIgnoringElementContentWhitespace(true);
        f.setIgnoringComments(true);
        f.setXIncludeAware(false);
        try {
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (final ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
        DBF = f;
    }

    // FIXME: these should be final
    private Instant start = null;
    private Instant stop = null;
    private String filter = null;
    private boolean leafNodesOnly = false;

    @VisibleForTesting
    public final Instant getStart() {
        return start;
    }

    /**
     * Set query parameters for listener.
     *
     * @param start         Start-time of getting notification.
     * @param stop          Stop-time of getting notification.
     * @param filter        Indicates which subset of all possible events are of interest.
     * @param leafNodesOnly If TRUE, notifications will contain changes of leaf nodes only.
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public void setQueryParams(final Instant start, final Instant stop, final String filter,
            final boolean leafNodesOnly) {
        this.start = requireNonNull(start);
        this.stop = stop;
        this.filter = filter;
        this.leafNodesOnly = leafNodesOnly;
    }

    /**
     * Check whether this query should only notify about leaf node changes.
     *
     * @return true if this query should only notify about leaf node changes
     */
    boolean getLeafNodesOnly() {
        return leafNodesOnly;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    <T extends BaseListenerInterface> boolean checkStartStop(final Instant now, final T listener) {
        if (this.stop != null) {
            if (this.start.compareTo(now) < 0 && this.stop.compareTo(now) > 0) {
                return true;
            }
            if (this.stop.compareTo(now) < 0) {
                try {
                    listener.close();
                } catch (final Exception e) {
                    throw new RestconfDocumentedException("Problem with unregister listener." + e);
                }
            }
        } else if (this.start != null) {
            if (this.start.compareTo(now) < 0) {
                this.start = null;
                return true;
            }
        } else {
            return true;
        }
        return false;
    }

    /**
     * Check if is filter used and then prepare and post data do client.
     *
     * @param xml XML data of notification.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    boolean checkFilter(final String xml) {
        if (this.filter == null) {
            return true;
        }
        try {
            return parseFilterParam(xml);
        } catch (final Exception e) {
            throw new RestconfDocumentedException("Problem while parsing filter.", e);
        }
    }

    /**
     * Parse and evaluate filter statement by XML format.
     *
     * @return {@code true} or {@code false} depending on filter expression and data of notification.
     * @throws Exception If operation fails.
     */
    private boolean parseFilterParam(final String xml) throws Exception {
        final Document docOfXml = DBF.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        final XPath xPath = XPathFactory.newInstance().newXPath();
        // FIXME: BUG-7956: xPath.setNamespaceContext(nsContext);
        return (boolean) xPath.compile(this.filter).evaluate(docOfXml, XPathConstants.BOOLEAN);
    }
}