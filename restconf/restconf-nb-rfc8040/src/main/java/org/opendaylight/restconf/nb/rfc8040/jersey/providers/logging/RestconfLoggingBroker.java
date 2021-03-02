/*
 * Copyright © 2021 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.logging;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.WriterInterceptorContext;
import org.apache.shiro.SecurityUtils;
import org.opendaylight.aaa.api.shiro.principal.ODLPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Broker used for logging of RESTCONF HTTP requests and responses based on provided
 * {@link RestconfLoggingConfiguration}. Requests and responses are logged separately - they can be paired
 * using session identifier.
 */
public final class RestconfLoggingBroker {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfLoggingBroker.class);
    private static final String LOG_ENTRIES_SEPARATOR = " | ";
    private static final String MESSAGE_ID_PROPERTY = "messageId";
    private static final String REQUEST_TIMESTAMP = "requestTimestamp";

    private final RestconfLoggingConfiguration loggingConfiguration;

    private BigInteger messageIdCounter = BigInteger.ZERO;

    /**
     * Creation of logging broker.
     *
     * @param loggingConfiguration RESTCONF logging configuration
     */
    public RestconfLoggingBroker(final RestconfLoggingConfiguration loggingConfiguration) {
        this.loggingConfiguration = loggingConfiguration;
    }

    /**
     * Log HTTP request.
     *
     * @param requestContext HTTP request context
     * @param servletRequest HTTP request servlet
     * @param requestBody    bytes of request body; {@code null}, if request doesn't contain body
     */
    public void logRequest(final ContainerRequestContext requestContext, final HttpServletRequest servletRequest,
                           final byte[] requestBody) {
        final BigInteger messageId = getAndIncrementMessageId();
        requestContext.setProperty(MESSAGE_ID_PROPERTY, messageId);
        requestContext.setProperty(REQUEST_TIMESTAMP, System.currentTimeMillis());

        final StringBuilder logBuilder = new StringBuilder();
        writeMessageId(messageId, logBuilder);
        writeRequestUriData(requestContext, logBuilder);
        writeRemoteHost(servletRequest, logBuilder);
        writeUserInfo(logBuilder);
        writeQueryParameters(requestContext.getUriInfo().getQueryParameters(), logBuilder);
        writeHttpHeaders(requestContext.getHeaders(), logBuilder);
        writeContentLength(requestBody, logBuilder);
        writeBody(requestBody, logBuilder);
        LOG.trace("REQ{}", logBuilder);
    }

    /**
     * Log HTTP response without response body.
     *
     * @param requestContext  HTTP request context
     * @param responseContext HTTP response response
     */
    public void logResponseWithoutBody(final ContainerRequestContext requestContext,
                                       final ContainerResponseContext responseContext) {
        final StringBuilder logBuilder = new StringBuilder();
        writeMessageId(requestContext.getProperty(MESSAGE_ID_PROPERTY), logBuilder);
        writeStatusCode(responseContext.getStatus(), logBuilder);
        writeDuration(requestContext.getProperty(REQUEST_TIMESTAMP), logBuilder);
        writeHttpHeaders(responseContext.getHeaders(), logBuilder);
        writeContentLength(null, logBuilder);
        LOG.trace("RES{}", logBuilder);
    }

    /**
     * Log HTTP response with response body.
     *
     * @param writerContext   writer context
     * @param servletResponse HTTP response servlet
     * @param responseBody    bytes HTTP response body
     */
    public void logResponseWithBody(final WriterInterceptorContext writerContext,
                                    final HttpServletResponse servletResponse, final byte[] responseBody) {
        final StringBuilder logBuilder = new StringBuilder();
        writeMessageId(writerContext.getProperty(MESSAGE_ID_PROPERTY), logBuilder);
        writeStatusCode(servletResponse.getStatus(), logBuilder);
        writeDuration(writerContext.getProperty(REQUEST_TIMESTAMP), logBuilder);
        writeHttpHeaders(writerContext.getHeaders(), logBuilder);
        writeContentLength(responseBody, logBuilder);
        writeBody(responseBody, logBuilder);
        LOG.trace("RES{}", logBuilder);
    }

    private synchronized BigInteger getAndIncrementMessageId() {
        final BigInteger currentMessageId = messageIdCounter;
        messageIdCounter = messageIdCounter.add(BigInteger.ONE);
        return currentMessageId;
    }

    private static void writeMessageId(final Object messageId, final StringBuilder logBuilder) {
        logBuilder.append(LOG_ENTRIES_SEPARATOR);
        if (messageId == null) {
            // this can happen when we enable logs in the middle of processing request
            return;
        }
        logBuilder.append(messageId);
    }

    private static void writeMessageId(final BigInteger messageId, final StringBuilder logBuilder) {
        logBuilder.append(LOG_ENTRIES_SEPARATOR)
                .append(messageId);
    }

    private static void writeRequestUriData(final ContainerRequestContext requestContext,
                                            final StringBuilder logBuilder) {
        logBuilder.append(LOG_ENTRIES_SEPARATOR)
                .append(requestContext.getMethod())
                .append(LOG_ENTRIES_SEPARATOR)
                .append(requestContext.getUriInfo().getPath());
    }

    private void writeRemoteHost(final HttpServletRequest servletRequest, final StringBuilder logBuilder) {
        logBuilder.append(LOG_ENTRIES_SEPARATOR)
                .append(servletRequest.getRemoteAddr())
                .append(":")
                .append(servletRequest.getRemotePort());
    }

    private void writeUserInfo(final StringBuilder logBuilder) {
        // is there more convenient way (without dependency on shiro-core), how to obtain ODL principal?
        final Object principal = SecurityUtils.getSubject().getPrincipal();
        logBuilder.append(LOG_ENTRIES_SEPARATOR);
        if (!(principal instanceof ODLPrincipal)) {
            // could this happen?
            return;
        }
        logBuilder.append(((ODLPrincipal) principal).getUserId());
    }

    private void writeQueryParameters(final MultivaluedMap<String, ?> parameters, final StringBuilder logBuilder) {
        if (!loggingConfiguration.isLoggingQueryParametersEnabled()) {
            return;
        }
        writeMap(parameters, logBuilder);
    }

    private void writeHttpHeaders(final MultivaluedMap<String, ?> parameters, final StringBuilder logBuilder) {
        if (!loggingConfiguration.isLoggingHeadersEnabled()) {
            return;
        }

        final Set<String> hiddenHttpHeaders = loggingConfiguration.getHiddenHttpHeaders();
        if (hiddenHttpHeaders.isEmpty()) {
            writeMap(parameters, logBuilder);
        } else {
            final MultivaluedHashMap<String, Object> filteredParameters = filterHttpHeaders(
                    parameters, hiddenHttpHeaders);
            writeMap(filteredParameters, logBuilder);
        }
    }

    private static MultivaluedHashMap<String, Object> filterHttpHeaders(final MultivaluedMap<String, ?> parameters,
                                                                        final Set<String> hiddenHttpHeaders) {
        final MultivaluedHashMap<String, Object> filteredHeaders = new MultivaluedHashMap<>();
        for (final Entry<String, ? extends List<?>> parameter : parameters.entrySet()) {
            if (!hiddenHttpHeaders.contains(parameter.getKey())) {
                filteredHeaders.addAll(parameter.getKey(), parameter.getValue());
            }
        }
        return filteredHeaders;
    }

    private static void writeMap(final MultivaluedMap<String, ?> parameters, final StringBuilder logBuilder) {
        logBuilder.append(LOG_ENTRIES_SEPARATOR)
                .append("{");
        final Iterator<? extends Entry<?, ? extends List<?>>> parametersIterator = parameters.entrySet().iterator();
        while (parametersIterator.hasNext()) {
            final Entry<?, ? extends List<?>> queryParameter = parametersIterator.next();
            logBuilder.append(queryParameter.getKey())
                    .append(":")
                    .append(queryParameter.getValue());
            if (parametersIterator.hasNext()) {
                logBuilder.append(", ");
            }
        }
        logBuilder.append("}");
    }

    private static void writeContentLength(final byte[] requestBody, final StringBuilder logBuilder) {
        logBuilder.append(LOG_ENTRIES_SEPARATOR)
                .append(requestBody == null ? 0 : requestBody.length);
    }

    private void writeBody(final byte[] requestBody, final StringBuilder logBuilder) {
        if (!loggingConfiguration.isLoggingBodyEnabled()) {
            return;
        }
        if (requestBody == null || requestBody.length == 0) {
            return;
        }
        logBuilder.append('\n')
                .append(new String(requestBody, StandardCharsets.UTF_8));
    }

    private static void writeStatusCode(final int statusCode, final StringBuilder logBuilder) {
        logBuilder.append(LOG_ENTRIES_SEPARATOR)
                .append(statusCode);
    }

    private static void writeDuration(final Object requestTimestamp, final StringBuilder logBuilder) {
        logBuilder.append(LOG_ENTRIES_SEPARATOR);
        if (requestTimestamp == null) {
            // this can happen when we enable logs in the middle of processing request
            return;
        }
        logBuilder.append(System.currentTimeMillis() - ((Long) requestTimestamp));
    }
}