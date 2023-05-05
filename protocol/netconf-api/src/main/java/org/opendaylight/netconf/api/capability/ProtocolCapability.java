/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.capability;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Enumeration of all known NETCONF protocol capabilities, as known in the
 * <a href="https://www.iana.org/assignments/netconf-capability-urns/netconf-capability-urns.xhtml">
 * Network Configuration Protocol (NETCONF) Capability URNs</a> IANA registry.
 */
public enum ProtocolCapability implements Capability {
    /**
     * The base NETCONF capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc4741.html#section-8.1">RFC4741, section 8.1</a>.
     * @deprecated This capability identifies legacy NETCONF devices and has been superseded by {@link #BASE_1_1}, just
     *             as RFC6241 obsoletes RFC4741.
     */
    @Deprecated
    BASE_1_0(":base:1.0", ProtocolCapabilityURN.BASE_1_0),
    /**
     * The base NETCONF capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.1">RFC6241, section 8.1</a>.
     */
    BASE_1_1(":base:1.1", ProtocolCapabilityURN.BASE_1_1),
    /**
     * The Candidate Configuration Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.3">RFC6241, section 8.3</a>.
     */
    CANDIDATE(":candidate", ProtocolCapabilityURN.CANDIDATE),
    /**
     * The Candidate Configuration Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc4741.html#section-8.3">RFC4741, section 8.3</a>.
     * @deprecated This capability is superseded by {@link #CONFIRMED_COMMIT_1_1}.
     */
    @Deprecated
    CONFIRMED_COMMIT(":confirmed-commit", ProtocolCapabilityURN.CONFIRMED_COMMIT),
    /**
     * The Rollback-on-Error Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.4">RFC6241, section 8.4</a>.
     */
    CONFIRMED_COMMIT_1_1(":confirmed-commit:1.1", ProtocolCapabilityURN.CONFIRMED_COMMIT_1_1),
    /**
     * The EXI Capability, as defined in
     * <a href="https://datatracker.ietf.org/doc/html/draft-varga-netconf-exi-capability-01#section-3">
     * draft-varga-netconf-exi-capability-01, section 3</a>. Note this is an IETF draft capability and subject to
     * change.
     */
    @Beta
    EXI(":exi", ProtocolCapabilityURN.EXI),
    /**
     * The Interleave Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc5277.html#section-6">RFC5277, section 6</a>.
     */
    INTERLEAVE(":interleave", ProtocolCapabilityURN.INTERLEAVE),
    /**
     * The Validate Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc5277.html#section-3.1">RFC5277, section 3.1</a>.
     */
    NOTIFICATION(":notification", ProtocolCapabilityURN.NOTIFICATION),
    /**
     * The Partial Locking Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc5717.html#section-2">RFC5715, section 2</a>.
     */
    PARTIAL_LOCK(":partial-lock", ProtocolCapabilityURN.PARTIAL_LOCK),
    /**
     * The Rollback-on-Error Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.5">RFC6241, section 8.5</a>.
     */
    ROLLBACK_ON_ERROR(":rollback-on-error", ProtocolCapabilityURN.ROLLBACK_ON_ERROR),
    /**
     * The Distinct Startup Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.7">RFC6241, section 8.7</a>.
     */
    STARTUP(":startup", ProtocolCapabilityURN.STARTUP),
    /**
     * The Time Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc7758.html#section-4">RFC7758, section 4</a>.
     */
    TIME_1_0(":time:1.0", ProtocolCapabilityURN.TIME_1_0),
    /**
     * The URL Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.8">RFC6241, section 8.8</a>.
     */
    URL(":url", ProtocolCapabilityURN.URL),
    /**
     * The Validate Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc4741.html#section-8.6">RFC4741, section 8.6</a>.
     * @deprecated This capability is superseded by {@link #VALIDATE_1_1}.
     */
    @Deprecated
    VALIDATE(":validate", ProtocolCapabilityURN.VALIDATE),
    /**
     * The Validate Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.6">RFC6241, section 8.6</a>.
     */
    VALIDATE_1_1(":validate:1.1", ProtocolCapabilityURN.VALIDATE_1_1),
    /**
     * The XPath Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.9">RFC6241, section 8.9</a>.
     */
    XPATH(":xpath", ProtocolCapabilityURN.XPATH),
    /**
     * The YANG Module Library Capability, as defined in
     * <a href="hhttps://www.rfc-editor.org/rfc/rfc7950.html#section-5.6.4">RFC7950, section 5.6.4</a> and further
     * specified by <a href="https://www.rfc-editor.org/rfc/rfc7895">RFC7895</a>. Note this applies to NETCONF endpoints
     * which DO NOT support Network Management Datastore Architecture as specified by
     * <a href="https://www.rfc-editor.org/rfc/rfc8342">RFC8342</a>.
     */
    YANG_LIBRARY(":yang-library", ProtocolCapabilityURN.YANG_LIBRARY),
    /**
     * The YANG Library Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8526.html#section-2">RFC8526, section 2</a> and further specified
     * by <a href="https://www.rfc-editor.org/rfc/rfc8525">RFC8525</a>. Note this applies to NETCONF endpoints
     * which DO support Network Management Datastore Architecture as specified by
     * <a href="https://www.rfc-editor.org/rfc/rfc8342">RFC8342</a>.
     */
    YANG_LIBRARY_1_1(":yang-library:1.1", ProtocolCapabilityURN.YANG_LIBRARY_1_1),
    /**
     * The With-defaults Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6243.html#section-4">RFC6243, section 4</a>.
     */
    WITH_DEFAULTS(":with-defaults", ProtocolCapabilityURN.WITH_DEFAULTS),
    /**
     * The With-defaults Capability, as augmented by
     * <a href="https://www.rfc-editor.org/rfc/rfc8526#section-3.1.1.2">RFC8526, section 3.1.1.2</a>.
     */
    WITH_OPERATIONAL_DEFAULTS(":with-operational-defaults", ProtocolCapabilityURN.WITH_OPERATIONAL_DEFAULTS),
    /**
     * The Writable-Running Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6241.html#section-8.2">RFC6241, section 8.2</a>.
     */
    WRITABLE_RUNNING(":writable-running", ProtocolCapabilityURN.WRITABLE_RUNNING);

    private final @NonNull String capabilityName;
    private final @NonNull String urn;

    ProtocolCapability(final String capabilityName, final String urn) {
        this.capabilityName = requireNonNull(capabilityName);
        this.urn = requireNonNull(urn);
    }

    public @NonNull String capabilityName() {
        return capabilityName;
    }

    @Override
    public @NonNull String urn() {
        return urn;
    }

    /**
     * Try to match a capability URN to a {@link ProtocolCapability}.
     *
     * @param urn URN to match
     * @return A {@link ProtocolCapability}
     * @throws NullPointerException if {@code urn} is {@code null}
     */
    public static @NonNull ProtocolCapability ofURN(final String urn) {
        final var capability = forURN(urn);
        if (capability == null) {
            throw new IllegalArgumentException(urn + " does not match a known protocol capability");
        }
        return capability;
    }

    /**
     * Match a capability URN to a {@link ProtocolCapability}.
     *
     * @param urn URN to match
     * @return A {@link ProtocolCapability}, or {@code null} the URN does not match a known protocol capability
     * @throws NullPointerException if {@code urn} is {@code null}
     *
     */
    public static @Nullable ProtocolCapability forURN(final String urn) {
        return switch (urn) {
            case ProtocolCapabilityURN.BASE_1_0 -> BASE_1_0;
            case ProtocolCapabilityURN.BASE_1_1 -> BASE_1_1;
            case ProtocolCapabilityURN.CANDIDATE -> CANDIDATE;
            case ProtocolCapabilityURN.CONFIRMED_COMMIT -> CONFIRMED_COMMIT;
            case ProtocolCapabilityURN.CONFIRMED_COMMIT_1_1 -> CONFIRMED_COMMIT_1_1;
            case ProtocolCapabilityURN.EXI -> EXI;
            case ProtocolCapabilityURN.INTERLEAVE -> INTERLEAVE;
            case ProtocolCapabilityURN.NOTIFICATION -> NOTIFICATION;
            case ProtocolCapabilityURN.PARTIAL_LOCK -> PARTIAL_LOCK;
            case ProtocolCapabilityURN.ROLLBACK_ON_ERROR -> ROLLBACK_ON_ERROR;
            case ProtocolCapabilityURN.STARTUP -> STARTUP;
            case ProtocolCapabilityURN.TIME_1_0 -> TIME_1_0;
            case ProtocolCapabilityURN.URL -> URL;
            case ProtocolCapabilityURN.VALIDATE -> VALIDATE;
            case ProtocolCapabilityURN.VALIDATE_1_1 -> VALIDATE_1_1;
            case ProtocolCapabilityURN.WITH_DEFAULTS -> WITH_DEFAULTS;
            case ProtocolCapabilityURN.WITH_OPERATIONAL_DEFAULTS -> WITH_OPERATIONAL_DEFAULTS;
            case ProtocolCapabilityURN.WRITABLE_RUNNING -> WRITABLE_RUNNING;
            case ProtocolCapabilityURN.XPATH -> XPATH;
            case ProtocolCapabilityURN.YANG_LIBRARY -> YANG_LIBRARY;
            case ProtocolCapabilityURN.YANG_LIBRARY_1_1 -> YANG_LIBRARY_1_1;
            default -> null;
        };
    }
}
