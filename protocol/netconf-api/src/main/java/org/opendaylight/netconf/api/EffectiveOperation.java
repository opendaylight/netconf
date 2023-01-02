/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;

/**
 * NETCONF modification actions, as allowed for in {@code operation} and {@code default-operation} attributes of
 * {@code <edit-config>} operation, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc6241#section-7.2">RFC6241 section 7.2</a>.
 *
 * <p>
 * This concept is uncharacteristically bound to two separate semantics, but for a good reason: at the end of the day we
 * want to know what the effective operation is.
 */
public enum EffectiveOperation {
    // operation and default-operation
    MERGE("merge",     true,  true),
    REPLACE("replace", true,  true),
    // operation only
    CREATE("create",   true,  false),
    DELETE("delete",   true,  false),
    REMOVE("remove",   true,  false),

    // default-operation-only
    NONE("none",       false, true);

    private final @NonNull String xmlValue;
    private final boolean isDefaultOperation;
    private final boolean isOperation;

    EffectiveOperation(final String xmlValue, final boolean isOperation, final boolean isDefaultOperation) {
        this.xmlValue = requireNonNull(xmlValue);
        this.isDefaultOperation = isDefaultOperation;
        this.isOperation = isOperation;
    }

    /**
     * Return the {@link EffectiveOperation} corresponding to a {@link #xmlValue}.
     *
     * @param xmlValue XML attribute or element value
     * @return A {@link EffectiveOperation}
     * @throws NullPointerException if {@code xmlValue} is {@code null}
     * @throws IllegalArgumentException if {@code xmlValue} is not recognized
     */
    public static @NonNull EffectiveOperation ofXmlValue(final String xmlValue) {
        return switch (xmlValue) {
            case "merge" -> MERGE;
            case "replace" -> REPLACE;
            case "remove" -> REMOVE;
            case "delete" -> DELETE;
            case "create" -> CREATE;
            case "none" -> NONE;
            default -> throw new IllegalArgumentException("Unknown operation " + xmlValue);
        };
    }

    /**
     * Return an XML string literal corresponding to this {@link EffectiveOperation}.
     *
     * @return An XML string literal
     */
    public @NonNull String xmlValue() {
        return xmlValue;
    }

    /**
     * Check if this operation is a candidate for {@code default-operation} argument.
     *
     * @return {@code true} if this operation can be used as {@code default-operation}, {@code false} otherwise.
     * @deprecated Use {@link #isDefaultOperation()} instead
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public boolean isAsDefaultPermitted() {
        return isDefaultOperation;
    }

    /**
     * Check if this operation is a candidate for {@code default-operation} argument.
     *
     * @return {@code true} if this operation can be used as {@code default-operation}, {@code false} otherwise.
     */
    public boolean isDefaultOperation() {
        return isDefaultOperation;
    }

    @Deprecated(since = "5.0.0", forRemoval = true)
    public boolean isOnElementPermitted() {
        return isOperation;
    }

    /**
     * Check if this operation is a candidate for {@code operation} attribute.
     *
     * @return {@code true} if this operation can be used as {@code operation}, {@code false} otherwise.
     */
    public boolean isOperation() {
        return isOperation;
    }
}
