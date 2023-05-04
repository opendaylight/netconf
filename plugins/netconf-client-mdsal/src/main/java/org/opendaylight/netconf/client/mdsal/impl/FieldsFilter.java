/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 * Copyright © 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * Definition of the subtree filter with single parent path and possibly multiple field sub-paths that are used
 * for reading/selection of specific entities.
 */
@Beta
public final class FieldsFilter implements Immutable {
    private final @NonNull YangInstanceIdentifier path;
    private final @NonNull List<YangInstanceIdentifier> fields;

    private FieldsFilter(final YangInstanceIdentifier path, final List<YangInstanceIdentifier> fields) {
        this.path = requireNonNull(path);
        this.fields = ImmutableList.copyOf(fields);
    }

    /**
     * Create a {@link FieldsFilter} using parent path and fields. Field paths are relative to parent path.
     *
     * @param path   parent query path
     * @param fields list of specific selection fields
     * @return instance of {@link FieldsFilter}
     * @throws NullPointerException if any argument is null, or if {@code fields} contains a null element
     */
    public static @NonNull FieldsFilter of(final YangInstanceIdentifier path,
            final List<YangInstanceIdentifier> fields) {
        return new FieldsFilter(path, fields);
    }

    /**
     * Get parent path.
     *
     * @return instance of {@link YangInstanceIdentifier}
     */
    public @NonNull YangInstanceIdentifier path() {
        return path;
    }

    /**
     * Get list of paths that narrows the filter for specific fields. Field paths are relative to parent path.
     *
     * @return {@link List} of field paths.
     */
    public @NonNull List<YangInstanceIdentifier> fields() {
        return fields;
    }
}