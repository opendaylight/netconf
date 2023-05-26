/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.mockito.ArgumentCaptor;
import org.opendaylight.restconf.openapi.model.Schema;

public final class DocGenTestHelper {

    private DocGenTestHelper() {
        // hidden on purpose
    }

    public static UriInfo createMockUriInfo(final String urlPrefix) throws Exception {
        final URI uri = new URI(urlPrefix);
        final UriBuilder mockBuilder = mock(UriBuilder.class);

        final ArgumentCaptor<String> subStringCapture = ArgumentCaptor.forClass(String.class);
        when(mockBuilder.path(subStringCapture.capture())).thenReturn(mockBuilder);
        when(mockBuilder.build()).then(invocation -> URI.create(uri + "/" + subStringCapture.getValue()));

        final UriInfo info = mock(UriInfo.class);
        when(info.getRequestUriBuilder()).thenReturn(mockBuilder);
        when(mockBuilder.replaceQuery(any())).thenReturn(mockBuilder);
        when(info.getBaseUri()).thenReturn(uri);

        return info;
    }

    /**
     * Checks whether object {@code mainObject} contains in properties/items key $ref with concrete value.
     */
    public static void containsReferences(final Schema mainObject, final String childObject,
            final String expectedRef) {
        final JsonNode properties = mainObject.properties();
        assertNotNull(properties);

        final JsonNode childNode = properties.get(childObject);
        assertNotNull(childNode);

        //list case
        JsonNode refWrapper = childNode.get("items");
        if (refWrapper == null) {
            //container case
            refWrapper = childNode;
        }
        assertEquals(expectedRef, refWrapper.get("$ref").asText());
    }
}
