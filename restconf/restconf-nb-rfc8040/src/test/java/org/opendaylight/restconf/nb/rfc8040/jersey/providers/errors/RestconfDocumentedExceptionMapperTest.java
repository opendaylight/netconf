/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors;

import javax.ws.rs.core.MediaType;
import org.junit.Test;

public class RestconfDocumentedExceptionMapperTest {

    @Test
    public void test() {
        MediaType mediaType = MediaType.valueOf("application/yang-data+xml");
        String subtype = mediaType.getSubtype();

    }

}