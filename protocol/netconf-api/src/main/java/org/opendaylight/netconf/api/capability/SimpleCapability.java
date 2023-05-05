/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.CapabilityURN;

/**
 * Enumeration of all simple NETCONF capabilities, i.e. those which do not have any additional parameters.
 */
public enum SimpleCapability implements Capability {
    /**
     * The base NETCONF capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc4741.html#section-8.1">RFC4741, section 8.1</a>.
     * @deprecated This capability identifies legacy NETCONF devices and has been superseded by {@link #BASE_1_1}, just
     *             as RFC6241 obsoletes RFC4741.
     */
    @Deprecated
    BASE(":base:1.0", CapabilityURN.BASE),
    /**
     * The base NETCONF capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.1">RFC6241, section 8.1</a>.
     */
    BASE_1_1(":base:1.1", CapabilityURN.BASE_1_1),
    /**
     * The Candidate Configuration Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.3">RFC6241, section 8.3</a>.
     */
    CANDIDATE(":candidate", CapabilityURN.CANDIDATE),
    /**
     * The Candidate Configuration Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc4741.html#section-8.3">RFC4741, section 8.3</a>.
     * @deprecated This capability is superseded by {@link #CONFIRMED_COMMIT_1_1}.
     */
    @Deprecated
    CONFIRMED_COMMIT(":confirmed-commit", CapabilityURN.CONFIRMED_COMMIT),
    /**
     * The Rollback-on-Error Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.4">RFC6241, section 8.4</a>.
     */
    CONFIRMED_COMMIT_1_1(":confirmed-commit:1.1", CapabilityURN.CONFIRMED_COMMIT_1_1),
    /**
     * The Interleave Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc5277.html#section-6">RFC5277, section 6</a>.
     */
    INTERLEAVE(":interleave", CapabilityURN.INTERLEAVE),
    /**
     * The Validate Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc5277.html#section-3.1">RFC5277, section 3.1</a>.
     */
    NOTIFICATION(":notification", CapabilityURN.NOTIFICATION),
    /**
     * The Partial Locking Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc5717.html#section-2">RFC5715, section 2</a>.
     */
    PARTIAL_LOCK(":partial-lock", CapabilityURN.PARTIAL_LOCK),
    /**
     * The Rollback-on-Error Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.5">RFC6241, section 8.5</a>.
     */
    ROLLBACK_ON_ERROR(":rollback-on-error", CapabilityURN.ROLLBACK_ON_ERROR),
    /**
     * The Distinct Startup Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.7">RFC6241, section 8.7</a>.
     */
    STARTUP(":startup", CapabilityURN.STARTUP),
    /**
     * The Time Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc7758.html#section-4">RFC7758, section 4</a>.
     */
    TIME(":time:1.0", CapabilityURN.TIME),
    /**
     * The URL Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.8">RFC6241, section 8.8</a>.
     */
    URL(":url", CapabilityURN.URL),
    /**
     * The Validate Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc4741.html#section-8.6">RFC4741, section 8.6</a>.
     * @deprecated This capability is superseded by {@link #VALIDATE_1_1}.
     */
    @Deprecated
    VALIDATE(":validate", CapabilityURN.VALIDATE),
    /**
     * The Validate Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.6">RFC6241, section 8.6</a>.
     */
    VALIDATE_1_1(":validate:1.1", CapabilityURN.VALIDATE_1_1),
    /**
     * The XPath Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.9">RFC6241, section 8.9</a>.
     */
    XPATH(":xpath", CapabilityURN.XPATH),
    /**
     * The YANG Module Library Capability, as defined in
     * <a href="hhttps://www.rfc-editor.org/rfc/rfc7950.html#section-5.6.4">RFC7950, section 5.6.4</a> and further
     * specified by <a href="https://www.rfc-editor.org/rfc/rfc7895">RFC7895</a>. Note this applies to NETCONF endpoints
     * which DO NOT support Network Management Datastore Architecture as specified by
     * <a href="https://www.rfc-editor.org/rfc/rfc8342">RFC8342</a>.
     */
    YANG_LIBRARY(":yang-library", CapabilityURN.YANG_LIBRARY),
    /**
     * The YANG Library Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8526.html#section-2">RFC8526, section 2</a> and further specified
     * by <a href="https://www.rfc-editor.org/rfc/rfc8525">RFC8525</a>. Note this applies to NETCONF endpoints
     * which DO support Network Management Datastore Architecture as specified by
     * <a href="https://www.rfc-editor.org/rfc/rfc8342">RFC8342</a>.
     */
    YANG_LIBRARY_1_1(":yang-library:1.1", CapabilityURN.YANG_LIBRARY_1_1),
    /**
     * The With-defaults Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6243.html#section-4">RFC6243, section 4</a>.
     */
    WITH_DEFAULTS(":with-defaults", CapabilityURN.WITH_DEFAULTS),
    /**
     * The With-defaults Capability, as augmented by
     * <a href="https://www.rfc-editor.org/rfc/rfc8526#section-3.1.1.2">RFC8526, section 3.1.1.2</a>.
     */
    WITH_OPERATIONAL_DEFAULTS(":with-operational-defaults", CapabilityURN.WITH_OPERATIONAL_DEFAULTS),
    /**
     * The Writable-Running Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.2">RFC6241, section 8.2</a>.
     */
    WRITABLE_RUNNING(":writable-running", CapabilityURN.WRITABLE_RUNNING);

    private final @NonNull String capabilityName;
    private final @NonNull String urn;
    private final @NonNull URI uri;

    SimpleCapability(final String capabilityName, final String urn) {
        this.capabilityName = requireNonNull(capabilityName);
        this.urn = requireNonNull(urn);
        uri = URI.create(urn);
    }

    public @NonNull String capabilityName() {
        return capabilityName;
    }

    @Override
    public String urn() {
        return urn;
    }

    @Override
    public URI toURI() {
        return uri;
    }

    /**
     * Try to match a capability URN to a {@link SimpleCapability}.
     *
     * @param urn URN to match
     * @return A {@link SimpleCapability}
     * @throws NullPointerException if {@code urn} is {@code null}
     */
    public static @NonNull SimpleCapability ofURN(final String urn) {
        final var capability = forURN(urn);
        if (capability == null) {
            throw new IllegalArgumentException(urn + " does not match a known protocol capability");
        }
        return capability;
    }

    /**
     * Match a capability URN to a {@link SimpleCapability}.
     *
     * @param urn URN to match
     * @return A {@link SimpleCapability}, or {@code null} the URN does not match a known protocol capability
     * @throws NullPointerException if {@code urn} is {@code null}
     *
     */
    public static @Nullable SimpleCapability forURN(final String urn) {
        return switch (urn) {
            case CapabilityURN.BASE -> BASE;
            case CapabilityURN.BASE_1_1 -> BASE_1_1;
            case CapabilityURN.CANDIDATE -> CANDIDATE;
            case CapabilityURN.CONFIRMED_COMMIT -> CONFIRMED_COMMIT;
            case CapabilityURN.CONFIRMED_COMMIT_1_1 -> CONFIRMED_COMMIT_1_1;
            case CapabilityURN.INTERLEAVE -> INTERLEAVE;
            case CapabilityURN.NOTIFICATION -> NOTIFICATION;
            case CapabilityURN.PARTIAL_LOCK -> PARTIAL_LOCK;
            case CapabilityURN.ROLLBACK_ON_ERROR -> ROLLBACK_ON_ERROR;
            case CapabilityURN.STARTUP -> STARTUP;
            case CapabilityURN.TIME -> TIME;
            case CapabilityURN.URL -> URL;
            case CapabilityURN.VALIDATE -> VALIDATE;
            case CapabilityURN.VALIDATE_1_1 -> VALIDATE_1_1;
            case CapabilityURN.WITH_DEFAULTS -> WITH_DEFAULTS;
            case CapabilityURN.WITH_OPERATIONAL_DEFAULTS -> WITH_OPERATIONAL_DEFAULTS;
            case CapabilityURN.WRITABLE_RUNNING -> WRITABLE_RUNNING;
            case CapabilityURN.XPATH -> XPATH;
            case CapabilityURN.YANG_LIBRARY -> YANG_LIBRARY;
            case CapabilityURN.YANG_LIBRARY_1_1 -> YANG_LIBRARY_1_1;
            default -> null;
        };
    }
}
