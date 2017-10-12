/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.streams.listeners;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.StringReader;
import java.time.Instant;
import java.util.Optional;
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
 *
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
     * @param start
     *            start-time of getting notification
     * @param stop
     *            stop-time of getting notification
     * @param filter
     *            indicate which subset of all possible events are of interest
     * @param leafNodesOnly
     *            if true, notifications will contain changes to leaf nodes only
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public void setQueryParams(final Instant start, final Optional<Instant> stop, final Optional<String> filter,
                               final boolean leafNodesOnly) {
        this.start = Preconditions.checkNotNull(start);
        this.stop = stop.orElse(null);
        this.filter = filter.orElse(null);
        this.leafNodesOnly = leafNodesOnly;
    }

    /**
     * Check whether this query should only notify about leaf node changes.
     *
     * @return true if this query should only notify about leaf node changes
     */
    public boolean getLeafNodesOnly() {
        return leafNodesOnly;
    }

    /**
     * Checking query parameters on specific notification.
     *
     * @param xml       data of notification
     * @param listener  listener of notification
     * @return true if notification meets the requirements of query parameters,
     *         false otherwise
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected <T extends BaseListenerInterface> boolean checkQueryParams(final String xml, final T listener) {
        final Instant now = Instant.now();
        if (this.stop != null) {
            if ((this.start.compareTo(now) < 0) && (this.stop.compareTo(now) > 0)) {
                return checkFilter(xml);
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
                return checkFilter(xml);
            }
        } else {
            return checkFilter(xml);
        }
        return false;
    }

    /**
     * Check if is filter used and then prepare and post data do client.
     *
     * @param xml   data of notification
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private boolean checkFilter(final String xml) {
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
     * Parse and evaluate filter value by xml.
     *
     * @return true or false - depends on filter expression and data of
     *         notifiaction
     * @throws Exception if operation fails
     */
    private boolean parseFilterParam(final String xml) throws Exception {
        final Document docOfXml = DBF.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        final XPath xPath = XPathFactory.newInstance().newXPath();
        // FIXME: BUG-7956: xPath.setNamespaceContext(nsContext);
        return (boolean) xPath.compile(this.filter).evaluate(docOfXml, XPathConstants.BOOLEAN);
    }
}
