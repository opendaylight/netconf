/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;

final class TransportUtils {
    private TransportUtils() {
        // utility class
    }

    static <K, V> @NonNull List<V> mapValues(final Map<K, ? extends V> map, final List<K> values,
            final String errorTemplate) throws UnsupportedConfigurationException {
        final var builder = ImmutableList.<V>builderWithExpectedSize(values.size());
        for (var value : values) {
            final var mapped = map.get(value);
            if (mapped == null) {
                throw new UnsupportedOperationException(String.format(errorTemplate, value));
            }
            builder.add(mapped);
        }
        return builder.build();
    }
}
