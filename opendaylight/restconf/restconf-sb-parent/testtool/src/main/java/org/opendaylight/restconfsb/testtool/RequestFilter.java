/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(RequestFilter.class);

    private static final int INDENT = 2;

    private static void logBody(final String body) {
        try {
            final Source xmlInput = new StreamSource(new StringReader(body));
            final StringWriter stringWriter = new StringWriter();
            final StreamResult xmlOutput = new StreamResult(stringWriter);
            final TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setAttribute("indent-number", INDENT);
            final Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(xmlInput, xmlOutput);
            LOG.debug("on body:\n{}", xmlOutput.getWriter().toString());
        } catch (final TransformerException e) {
            LOG.error("Failed to indent xml file", e);
        }
    }

    private static void logMethod(final String method, final String requestURL) {
        LOG.info("operation: {} on path {}", method, requestURL);
    }

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        //not supported
    }

    @Override
    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain filterChain) throws IOException, ServletException {
        final ResettableStreamHttpServletRequest wrappedRequest = new ResettableStreamHttpServletRequest(
                (HttpServletRequest) servletRequest);
        final String body = IOUtils.toString(wrappedRequest.getReader());
        logMethod(wrappedRequest.getMethod(), wrappedRequest.getRequestURL().toString());
        if (!body.isEmpty()) {
            logBody(body);
        }
        wrappedRequest.resetInputStream();
        filterChain.doFilter(wrappedRequest, servletResponse);
    }

    @Override
    public void destroy() {
        throw new UnsupportedOperationException("operation is not implemented.");
    }

    private static class ResettableStreamHttpServletRequest extends
            HttpServletRequestWrapper {

        private byte[] rawData;
        private final HttpServletRequest request;
        private final ResettableServletInputStream servletStream;

        public ResettableStreamHttpServletRequest(final HttpServletRequest request) {
            super(request);
            this.request = request;
            this.servletStream = new ResettableServletInputStream();
        }

        public void resetInputStream() {
            servletStream.stream = new ByteArrayInputStream(rawData);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (rawData == null) {
                rawData = IOUtils.toByteArray(this.request.getReader());
                servletStream.stream = new ByteArrayInputStream(rawData);
            }
            return servletStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }


        private class ResettableServletInputStream extends ServletInputStream {

            private InputStream stream;

            @Override
            public int read() throws IOException {
                return stream.read();
            }

            @Override
            public int available() throws IOException {
                return rawData.length;
            }
        }
    }
}
