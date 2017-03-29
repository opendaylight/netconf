/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.api.http;

import com.google.common.net.MediaType;
import java.util.Objects;

/**
 * Data class encapsulating request parameters.
 */
public class Request {
    private final String path;
    private final String body;
    private final RestconfMediaType type;

    private Request(final String path, final String body, final RestconfMediaType type) {
        this.path = path;
        this.body = body;
        this.type = type;
    }

    /**
     * @param path restconf resource path, in form e.g. /example-jukebox:jukebox/library
     * @param body body
     * @param type media type
     * @return request
     */
    public static Request createRequestWithBody(final String path, final String body, final RestconfMediaType type) {
        return new Request(path, body, type);
    }

    /**
     * @param path restconf resource path, in form e.g. /example-jukebox:jukebox/library
     * @param type media type
     * @return request
     */
    public static Request createRequestWithoutBody(final String path, final RestconfMediaType type) {
        return new Request(path, "", type);
    }

    /**
     * @return resource path
     */
    public String getPath() {
        return path;
    }

    /**
     * @return request body
     */
    public String getBody() {
        return body;
    }

    /**
     * @return Content-Type and Accepts media type
     */
    public RestconfMediaType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Request{" +
                "configPath='" + path + '\'' +
                ", body='" + body + '\'' +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Request request = (Request) o;
        return Objects.equals(path, request.path) &&
                Objects.equals(body, request.body) &&
                type == request.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, body, type);
    }

    public enum RestconfMediaType {
        XML(MediaType.APPLICATION_XML_UTF_8.toString()),
        JSON(MediaType.JSON_UTF_8.toString()),
        XML_DATA(MediaType.create(MediaType.ANY_APPLICATION_TYPE.type(), "yang.data+xml").toString()),
        XML_API(MediaType.create(MediaType.ANY_APPLICATION_TYPE.type(), "yang.api+xml").toString()),
        XML_OPERATION(MediaType.create(MediaType.ANY_APPLICATION_TYPE.type(), "yang.operation+xml").toString()),
        XML_DATASTORE(MediaType.create(MediaType.ANY_APPLICATION_TYPE.type(), "yang.datastore+xml").toString()),
        JSON_DATA(MediaType.create(MediaType.ANY_APPLICATION_TYPE.type(), "yang.data+json").toString()),
        JSON_API(MediaType.create(MediaType.ANY_APPLICATION_TYPE.type(), "yang.api+json").toString()),
        JSON_OPERATION(MediaType.create(MediaType.ANY_APPLICATION_TYPE.type(), "yang.operation+json").toString()),
        JSON_DATASTORE(MediaType.create(MediaType.ANY_APPLICATION_TYPE.type(), "yang.datastore+json").toString());

        private final String value;

        RestconfMediaType(final String value) {
            this.value = value;
        }

        public String getHeaderValue() {
            return value;
        }
    }
}
