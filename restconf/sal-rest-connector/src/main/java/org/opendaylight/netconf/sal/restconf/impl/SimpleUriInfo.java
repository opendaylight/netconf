/*
 * Copyright (c) 2017 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

public class SimpleUriInfo implements UriInfo {
    private final String path;
    private final MultivaluedMap<String, String> queryParams;

    public SimpleUriInfo(final String path) {
        this(path, new MultivaluedHashMap<>());
    }

    public SimpleUriInfo(final String path, final MultivaluedMap<String, String> queryParams) {
        this.path = path;
        this.queryParams = queryParams;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getPath(final boolean decode) {
        return path;
    }

    @Override
    public List<PathSegment> getPathSegments() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<PathSegment> getPathSegments(final boolean decode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public URI getRequestUri() {
        return URI.create(path);
    }

    @Override
    public UriBuilder getRequestUriBuilder() {
        return UriBuilder.fromUri(getRequestUri());
    }

    @Override
    public URI getAbsolutePath() {
        return getRequestUri();
    }

    @Override
    public UriBuilder getAbsolutePathBuilder() {
        return UriBuilder.fromUri(getAbsolutePath());
    }

    @Override
    public URI getBaseUri() {
        return URI.create("");
    }

    @Override
    public UriBuilder getBaseUriBuilder() {
        return UriBuilder.fromUri(getBaseUri());
    }

    @Override
    public MultivaluedMap<String, String> getPathParameters() {
        return new MultivaluedHashMap<>();
    }

    @Override
    public MultivaluedMap<String, String> getPathParameters(final boolean decode) {
        return getPathParameters();
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters() {
        return queryParams;
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters(final boolean decode) {
        return getQueryParameters();
    }

    @Override
    public List<String> getMatchedURIs() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getMatchedURIs(final boolean decode) {
        return getMatchedURIs();
    }

    @Override
    public List<Object> getMatchedResources() {
        return Collections.emptyList();
    }

    @Override
    public URI resolve(final URI uri) {
        return uri;
    }

    @Override
    public URI relativize(final URI uri) {
        return uri;
    }
}