/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.util;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

/**
 * Simple implementation of the {@link UriInfo} interface.
 *
 * @author Thomas Pantelis
 */
public class SimpleUriInfo implements UriInfo {
    private final String path;
    private final MultivaluedMap<String, String> queryParams;

    public SimpleUriInfo(String path) {
        this(path, new MultivaluedHashMap<>());
    }

    public SimpleUriInfo(String path, MultivaluedMap<String, String> queryParams) {
        this.path = path;
        this.queryParams = queryParams;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getPath(boolean decode) {
        return path;
    }

    @Override
    public List<PathSegment> getPathSegments() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<PathSegment> getPathSegments(boolean decode) {
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
    public MultivaluedMap<String, String> getPathParameters(boolean decode) {
        return getPathParameters();
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters() {
        return queryParams;
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters(boolean decode) {
        return getQueryParameters();
    }

    @Override
    public List<String> getMatchedURIs() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getMatchedURIs(boolean decode) {
        return getMatchedURIs();
    }

    @Override
    public List<Object> getMatchedResources() {
        return Collections.emptyList();
    }

    @Override
    public URI resolve(URI uri) {
        return uri;
    }

    @Override
    public URI relativize(URI uri) {
        return uri;
    }
}
