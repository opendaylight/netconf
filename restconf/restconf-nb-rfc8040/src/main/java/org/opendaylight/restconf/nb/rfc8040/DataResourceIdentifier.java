/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * Type-safe encapsulation of a Data Resource Identifier as encoded in the request URI. This class is an intermediary
 * between JAX-RS layer and service implementations. Encoded form is expected to conform to
 * <a href="https://tools.ietf.org/html/rfc8040#section-3.5.3">RFC8040 section 3.5.3</a>. Note that due to
 * {@code action} invocation is defined in terms of the Data Resource, it can also be encoding a
 * {@code &lt;data-resource-identifier&gt;/&lt;action&gt;} combination. Users are expected to properly disambiguate
 * such use.
 */
@Beta
public final class DataResourceIdentifier implements Immutable {
    private final @NonNull String encodedForm;

    private DataResourceIdentifier(final String encodedForm) {
        this.encodedForm = requireNonNull(encodedForm);
    }

    public static @NonNull DataResourceIdentifier valueOf(final String encodedForm) {
        return new DataResourceIdentifier(encodedForm);
    }

    public @NonNull String encodedForm() {
        return encodedForm;
    }

    @Override
    public int hashCode() {
        return encodedForm.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || obj instanceof DataResourceIdentifier
                && encodedForm.equals(((DataResourceIdentifier) obj).encodedForm);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("encodedForm", encodedForm).toString();
    }
}
