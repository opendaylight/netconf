/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.Map;

public enum Encoding {
    ENCODE_XML("encode-xml"), ENCODE_JSON("encode-json");

    private static final Map<String, Encoding> ENCODING_MAP = Maps.uniqueIndex(
            Arrays.asList(Encoding.values()), Encoding::getKeyword);

    private final String keyword;

    Encoding(final String keyword) {
        this.keyword = keyword;
    }

    public String getKeyword() {
        return this.keyword;
    }

    public static Encoding parse(final String keyword) {
        return ENCODING_MAP.get(requireNonNull(keyword));
    }
}
