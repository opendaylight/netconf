/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A simple DTO definitiong an <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15">HTTP Status Code</a>. Integer
 * values used here are assigned through the
 * <a href="https://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml">IANA Status Code Registry</a>.
 */
@Beta
@NonNullByDefault
public final class HttpStatusCode {
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.3.1">200 OK</a>.
     */
    public static final HttpStatusCode OK = new HttpStatusCode(200, "OK");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.3.2">201 Created</a>.
     */
    public static final HttpStatusCode CREATED = new HttpStatusCode(201, "Created");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.3.3">202 Accepted</a>.
     */
    public static final HttpStatusCode ACCEPTED = new HttpStatusCode(202, "Accepted");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.3.4">203 Non-Authoritative Information</a>.
     */
    public static final HttpStatusCode NON_AUTHORITATIVE_INFORMATION =
        new HttpStatusCode(203, "Non-Authoritative Information");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.3.5">204 No Content</a>.
     */
    public static final HttpStatusCode NO_CONTENT = new HttpStatusCode(204, "No Content");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.3.6">205 Reset Content</a>.
     */
    public static final HttpStatusCode RESET_CONTENT = new HttpStatusCode(205, "Reset Content");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.3.7">206 Partial Content</a>.
     */
    public static final HttpStatusCode PARTIAL_CONTENT = new HttpStatusCode(206, "Partial Content");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.4.1">300 Multiple Choices</a>.
     */
    public static final HttpStatusCode MULTIPLE_CHOICES = new HttpStatusCode(300, "Multiple Choices");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.4.2">301 Moved Permanently</a>.
     */
    public static final HttpStatusCode MOVED_PERMANENTLY = new HttpStatusCode(301, "Moved Permanently");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.4.3">302 Found</a>.
     */
    public static final HttpStatusCode FOUND = new HttpStatusCode(302, "Found");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.4.4">303 See Other</a>.
     */
    public static final HttpStatusCode SEE_OTHER = new HttpStatusCode(303, "See Other");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.4.5">304 Not Modified</a>.
     */
    public static final HttpStatusCode NOT_MODIFIED = new HttpStatusCode(304, "Not Modified");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.4.6">305 Use Proxy</a>.
     */
    public static final HttpStatusCode USE_PROXY = new HttpStatusCode(305, "Use Proxy");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.4.8">307 Temporary Redirect</a>.
     */
    public static final HttpStatusCode TEMPORARY_REDIRECT = new HttpStatusCode(307, "Temporary Redirect");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.4.9">308 Permanent Redirect</a>.
     */
    public static final HttpStatusCode PERMANENT_REDIRECT = new HttpStatusCode(308, "Permanent Redirect");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.1">400 Bad Request</a>.
     */
    public static final HttpStatusCode BAD_REQUEST = new HttpStatusCode(400, "Bad Request");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.2">401 Unauthorized</a>.
     */
    public static final HttpStatusCode UNAUTHORIZED = new HttpStatusCode(401, "Unauthorized");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.3">402 Payment Required</a>.
     */
    public static final HttpStatusCode PAYMENT_REQUIRED = new HttpStatusCode(402, "Payment Required");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.4">403 Forbidden</a>.
     */
    public static final HttpStatusCode FORBIDDEN = new HttpStatusCode(403, "Forbidden");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.5">404 Not Found</a>.
     */
    public static final HttpStatusCode NOT_FOUND = new HttpStatusCode(404, "Not Found");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.6">405 Method Not Allowed</a>.
     */
    public static final HttpStatusCode METHOD_NOT_ALLOWED = new HttpStatusCode(405, "Method Not Allowed");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.7">406 Not Acceptable</a>.
     */
    public static final HttpStatusCode NOT_ACCEPTABLE = new HttpStatusCode(406, "Not Acceptable");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.8">407 Proxy Authentication Required</a>.
     */
    public static final HttpStatusCode PROXY_AUTHENTICATION_REQUIRED =
        new HttpStatusCode(407, "Proxy Authentication Required");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.9">408 Request Timeout</a>.
     */
    public static final HttpStatusCode REQUEST_TIMEOUT = new HttpStatusCode(408, "Request Timeout");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.10">409 Conflict</a>.
     */
    public static final HttpStatusCode CONFLICT = new HttpStatusCode(409, "Conflict");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.11">410 Gone</a>.
     */
    public static final HttpStatusCode GONE = new HttpStatusCode(410, "Gone");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.12">411 Length Required</a>.
     */
    public static final HttpStatusCode LENGTH_REQUIRED = new HttpStatusCode(411, "Length Required");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.13">412 Precondition Failed</a>.
     */
    public static final HttpStatusCode PRECONDITION_FAILED = new HttpStatusCode(412, "Precondition Failed");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.14">413 Content Too Large</a>.
     */
    public static final HttpStatusCode CONTENT_TOO_LARGE = new HttpStatusCode(413, "Content Too Large");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.15">414 Content Too Long</a>.
     */
    public static final HttpStatusCode URI_TOO_LONG = new HttpStatusCode(414, "URI Too Long");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.16">415 Unsupported Media Type</a>.
     */
    public static final HttpStatusCode UNSUPPORTED_MEDIA_TYPE = new HttpStatusCode(415, "Unsupported Media Type");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.17">416 Requested Range Not Satisfiable</a>.
     */
    public static final HttpStatusCode REQUESTED_RANGE_NOT_SATISFIABLE =
        new HttpStatusCode(416, "Requested Range Not Satisfiable");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.18">417 Expectation Failed</a>.
     */
    public static final HttpStatusCode EXPECTATION_FAILED = new HttpStatusCode(417, "Expectation Failed");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.19">418 (Unused)</a>.
     */
    @Deprecated(forRemoval = true)
    public static final HttpStatusCode I_M_A_TEAPOT = new HttpStatusCode(418, "I'm a teapot");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.20">421 Misdirected Request</a>.
     */
    public static final HttpStatusCode MISDIRECTED_REQUEST = new HttpStatusCode(421, "Misdirected Request");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.21">422 Unprocessable Content</a>.
     */
    public static final HttpStatusCode UNPROCESSABLE_CONTENT = new HttpStatusCode(422, "Unprocessable Content");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.5.22">426 Upgrade Required</a>.
     */
    public static final HttpStatusCode UPGRADE_REQUIRED = new HttpStatusCode(426, "Upgrade Required");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc6585#section-3">428 Precondition Required</a>.
     */
    public static final HttpStatusCode PRECONDITION_REQUIRED = new HttpStatusCode(428, "Precondition Required");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc6585#section-4">429 Too Many Requests</a>.
     */
    public static final HttpStatusCode TOO_MANY_REQUESTS = new HttpStatusCode(429, "Too Many Requests");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc6585#section-5">431 Request Header Fields Too Large</a>.
     */
    public static final HttpStatusCode REQUEST_HEADER_FIELDS_TOO_LARGE =
        new HttpStatusCode(431, "Request Header Fields Too Large");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.6.1">500 Internal Server Error</a>.
     */
    public static final HttpStatusCode INTERNAL_SERVER_ERROR = new HttpStatusCode(500, "Internal Server Error");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.6.2">501 Not Implemented</a>.
     */
    public static final HttpStatusCode NOT_IMPLEMENTED = new HttpStatusCode(501, "Not Implemented");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.6.3">502 Bad Gateway</a>.
     */
    public static final HttpStatusCode BAD_GATEWAY = new HttpStatusCode(502, "Bad Gateway");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.6.4">503 Service Unavailable</a>.
     */
    public static final HttpStatusCode SERVICE_UNAVAILABLE = new HttpStatusCode(503, "Service Unavailable");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.6.5">504 Gateway Timeout</a>.
     */
    public static final HttpStatusCode GATEWAY_TIMEOUT = new HttpStatusCode(504, "Gateway Timeout");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9110#section-15.6.6">505 HTTP Version Not Supported</a>.
     */
    public static final HttpStatusCode HTTP_VERSION_NOT_SUPPORTED =
        new HttpStatusCode(505, "HTTP Version Not Supported");
    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc6585#section-6">511 Network Authentication Required</a>.
     */
    public static final HttpStatusCode NETWORK_AUTHENTICATION_REQUIRED =
        new HttpStatusCode(511, "Network Authentication Required");

    private final int code;
    private final String phrase;

    public HttpStatusCode(final int code, final @Nullable String phrase) {
        if (code < 100 || code > 599) {
            throw new IllegalArgumentException("Invalid statusCode " + code);
        }
        this.code = code;
        this.phrase = phrase;
    }

    /**
     * Returns the HTTP status code, {@code 100-599}.
     *
     * @return the HTTP status code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the phrase or {@code null}.
     *
     * @return the phrase or {@code null}
     */
    public @Nullable String phrase() {
        return phrase;
    }

    @Override
    public int hashCode() {
        return code;
    }

    @Override
    public boolean equals(final @Nullable Object obj) {
        return obj == this || obj instanceof HttpStatusCode other && code == other.code;
    }

    @Override
    public String toString() {
        return phrase == null ? Integer.toString(code) : code + " " + phrase;
    }
}
