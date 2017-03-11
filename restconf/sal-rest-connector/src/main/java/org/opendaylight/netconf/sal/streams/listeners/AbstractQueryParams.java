/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.streams.listeners;

import java.io.StringReader;
import java.util.Date;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Features of query parameters part of both notifications
 *
 */
abstract class AbstractQueryParams extends AbstractNotificationsData {

    protected Date start = null;
    protected Date stop = null;
    protected String filter = null;

    private String xml;

    /**
     * Set query parameters for listener
     *
     * @param start
     *            - start-time of getting notification
     * @param stop
     *            - stop-time of getting notification
     * @param filter
     *            - indicate which subset of all possible events are of interest
     */
    public void setQueryParams(final Date start, final Date stop, final String filter) {
        this.start = start;
        this.stop = stop;
        this.filter = filter;
    }

    /**
     * Checking query parameters on specific notification
     *
     * @param xml
     *            - data of notification
     * @param listener
     *            - listener of notification
     * @return true if notification meets the requirements of query parameters,
     *         false otherwise
     */
    protected <T extends BaseListenerInterface> boolean checkQueryParams(final String xml, final T listener) {
        this.xml = xml;
        final Date now = new Date();
        if (this.stop != null) {
            if ((this.start.compareTo(now) < 0) && (this.stop.compareTo(now) > 0)) {
                return checkFilter();
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
                return checkFilter();
            }
        } else {
            return checkFilter();
        }
        return false;
    }

    /**
     * Check if is filter used and then prepare and post data do client
     *
     * @param change
     *            - data of notification
     */
    private boolean checkFilter() {
        if (this.filter == null) {
            return true;
        } else {
            try {
                return parseFilterParam();
            } catch (final Exception e) {
                throw new RestconfDocumentedException("Problem while parsing filter.", e);
            }
        }
    }

    /**
     * Parse and evaluate filter value by xml
     *
     * @return true or false - depends on filter expression and data of
     *         notifiaction
     * @throws Exception
     */
    private boolean parseFilterParam() throws Exception {
        final Document docOfXml = UntrustedXML.newDocumentBuilder().parse(new InputSource(new StringReader(this.xml)));
        final XPath xPath = XPathFactory.newInstance().newXPath();
        return (boolean) xPath.compile(this.filter).evaluate(docOfXml, XPathConstants.BOOLEAN);
    }
}
