#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

import json
import logging
import textwrap

import allure
import pytest

from libraries import aaa_shiro_config as shiro
from libraries import infra
from libraries import jaxrs_restconf
from libraries import oauth_proxy_header
from libraries import utils
from suites.suite_order import SuiteOrder

# aaa-shiro's Oauth2ProxyHeaderFilter/Realm trust a reverse proxy's
# X-Forwarded-User / X-Forwarded-Groups headers instead of validating any
# token themselves -- the real OAuth2/OIDC handshake is assumed to have
# already happened upstream. The filter is only set for one specific
# RESTCONF endpoint, ietf-yang-library:yang-library. The suite's
# shiro-configuration replaces the whole `urls` list, so it ends with the
# default "/** = authcBasic" catch-all: every other URL (including the
# suite's own data/aaa-app-config writes) still requires Basic auth
# (admin/admin), and the proxy-header chain is exercised alongside it.
#
# Sent to the JAX-RS RESTCONF endpoint (odl-restconf-nb-jaxrs), not the
# Netty one (odl-restconf-nb) since only the JAX-RS supports oauth.
PROTECTED_URI = f"{jaxrs_restconf.RESTCONF_ROOT}/data/ietf-yang-library:yang-library"
REQUIRED_ROLE = "netconf-admin-role"
OAUTH2_PROXY_HEADER_SHIRO_CONFIGURATION_FILE = (
    "variables/oauth/proxy_header/shiro_configuration.json"
)

log = logging.getLogger(__name__)


def _wait_until_user_gets_status(
    username: str, groups: str, expected_code: int, timeout: int = 30
):
    """Polls a request with the given proxy headers until it gets
    `expected_code`, confirming which configuration is live.

    Args:
        username (str): Value to send as X-Forwarded-User.
        groups (str): Value to send as X-Forwarded-Groups.
        expected_code (int): Status code to wait for.
        timeout (int): Seconds to keep polling, one request per second.
    """
    utils.wait_until_function_pass(
        timeout,
        1,
        oauth_proxy_header.get_with_proxy_headers,
        PROTECTED_URI,
        username=username,
        groups=groups,
        expected_code=expected_code,
    )


def _wait_until_unauthorized_user_gets_status(expected_code: int, timeout: int = 30):
    """Polls a request from an authenticated but unauthorized user (one
    without the required role) until it gets `expected_code`, confirming
    which `roles` filter is live.

    Args:
        expected_code (int): 403 for ODLRolesAuthorizationFilter, 401 for
            Shiro's default `roles` filter.
        timeout (int): Seconds to keep polling, one request per second.
    """
    _wait_until_user_gets_status(
        "bob", "some-other-role", expected_code, timeout=timeout
    )


@pytest.mark.authentication
@pytest.mark.functional
@pytest.mark.standalone
@pytest.mark.oauth
@pytest.mark.usefixtures("odl_standalone")
@pytest.mark.run(order=SuiteOrder.OAUTH_PROXY_HEADER)
class TestOAuthProxyHeader:
    """Tests for aaa-shiro's OAuth2 Proxy Header authentication.

    ODL never validates a token here: it trusts X-Forwarded-User /
    X-Forwarded-Groups headers as if a reverse proxy had already performed
    the OAuth2/OIDC handshake. No JWT token involved.
    """

    @pytest.fixture(scope="class", autouse=True)
    def suite_setup(self, request, jaxrs_restconf_endpoint):
        """Wires the Oauth2ProxyHeaderFilter/Realm in for the suite on the
        JAX-RS RESTCONF endpoint (the only one this feature is reachable
        through, installed by jaxrs_restconf_endpoint), and reverts this at
        the end so other suites are unaffected.

        Uses infra.log_step directly rather than the
        allure_step_with_separate_logging fixture: that fixture is
        function-scoped, and pytest doesn't allow this class-scoped fixture
        to depend on it.

        The restore is registered as a finalizer before the write, so it
        still runs if anything after the write fails (e.g. ODL not coming
        back up after the restart).
        """
        with infra.log_step("step_save_initial_shiro_configuration"):
            initial_state = shiro.read_shiro_configuration()

        def _restore():
            with infra.log_step("step_restore_initial_shiro_configuration"):
                shiro.write_shiro_configuration_and_restart(initial_state)

        request.addfinalizer(_restore)
        with infra.log_step("step_set_oauth2_proxy_header_shiro_configuration"):
            proxy_header_shiro_config = json.loads(
                infra.get_file_content(OAUTH2_PROXY_HEADER_SHIRO_CONFIGURATION_FILE)
            )
            shiro.write_shiro_configuration_and_restart(proxy_header_shiro_config)

    @pytest.fixture
    def override_parser_config(self, request, allure_step_with_separate_logging):
        """Returns a callable that rewrites the oauth2proxyheaderparser
        config to custom settings, with any parameter not given falling
        back to its default, restoring plain defaults at the end. Used by
        tests that need custom validation settings for the header values.
        """

        def _set(overrides: dict):
            with allure_step_with_separate_logging("step_set_parser_config"):
                oauth_proxy_header.set_parser_config(overrides)

        def _reset():
            with allure_step_with_separate_logging("step_reset_parser_config"):
                oauth_proxy_header.reset_parser_config()

        request.addfinalizer(_reset)
        return _set

    @pytest.fixture
    def odl_403_roles_filter_settings(self, request, allure_step_with_separate_logging):
        """Temporarily overrides Shiro's built-in `roles` filter with
        ODLRolesAuthorizationFilter, reverting it afterwards. Used by the
        tests that check the 403 variant of an authorization-failure
        scenario - every other test relies on Shiro's own default (401)
        behavior.

        The revert is registered as a finalizer before the write, so it
        still runs if the write's reload confirmation fails.

        A live reload alone isn't reliable proof the filter-chain change
        was picked up (see write_shiro_configuration_and_restart), so
        both the override and its revert are confirmed by polling a
        wrong-role request until it gets the expected status: 403 with the
        override, 401 once Shiro's default `roles` filter is back.
        """
        with allure_step_with_separate_logging("step_save_initial_roles_filter"):
            initial_value = shiro.read_main_pair_value(shiro.ROLES_KEY)

        def _revert():
            with allure_step_with_separate_logging("step_revert_roles_filter"):
                shiro.write_main_pair_value(shiro.ROLES_KEY, initial_value)
                _wait_until_unauthorized_user_gets_status(401)

        request.addfinalizer(_revert)
        with allure_step_with_separate_logging("step_set_odl_403_roles_filter"):
            shiro.write_main_pair_value(
                shiro.ROLES_KEY, shiro.ODL_ROLES_AUTHORIZATION_FILTER_CLASS
            )
            _wait_until_unauthorized_user_gets_status(403)

    # -----------------------------------------------------------------
    # Positive & Happy Path (HTTP 200)
    # -----------------------------------------------------------------

    @pytest.mark.smoke
    @allure.description(
        "User with proper permissions using Proxy Headers receives a 200."
    )
    def test_standard_success(self, allure_step_with_separate_logging):
        with allure_step_with_separate_logging("step_send_request"):
            oauth_proxy_header.get_with_proxy_headers(
                PROTECTED_URI,
                username="alice",
                groups=REQUIRED_ROLE,
                expected_code=200,
            )

    @pytest.mark.smoke
    @allure.description(
        textwrap.dedent(
            """
            User with a proper Proxy Header carrying multiple made-up roles, \
            plus one valid role, still receives a 200.
            """
        )
    )
    def test_partial_role_match(self, allure_step_with_separate_logging):
        with allure_step_with_separate_logging("step_send_request"):
            oauth_proxy_header.get_with_proxy_headers(
                PROTECTED_URI,
                username="alice",
                groups=f"made-up-role-1,made-up-role-2,{REQUIRED_ROLE}",
                expected_code=200,
            )

    @pytest.mark.smoke
    @allure.description(
        textwrap.dedent(
            """
            User with a proper Proxy Header whose group value carries the \
            `role:` prefix (stripped by Oauth2ProxyHeaderParserImpl before \
            matching) still receives a 200.
            """
        )
    )
    def test_prefixed_role_matching(self, allure_step_with_separate_logging):
        with allure_step_with_separate_logging("step_send_request"):
            oauth_proxy_header.get_with_proxy_headers(
                PROTECTED_URI,
                username="alice",
                groups=f"role:{REQUIRED_ROLE}",
                expected_code=200,
            )

    # -----------------------------------------------------------------
    # Authorization Failures (HTTP 401 / 403)
    # -----------------------------------------------------------------
    # Shiro's default roles filter always denies an authenticated-but-
    # wrong-role (or no-role) request with 401, never 403 - the tests below
    # exercise that default. aaa-shiro ships an opt-in replacement that
    # sends 403 instead once the subject is known to be authenticated -
    # their counterparts below exercise that instead, via the
    # odl_403_roles_filter_settings fixture.

    @pytest.mark.smoke
    @allure.description(
        "User without proper permission using Proxy Headers receives a 401."
    )
    def test_insufficient_permissions(self, allure_step_with_separate_logging):
        with allure_step_with_separate_logging("step_send_request"):
            oauth_proxy_header.get_with_proxy_headers(
                PROTECTED_URI,
                username="bob",
                groups="some-other-role",
                expected_code=401,
            )

    @allure.description(
        textwrap.dedent(
            """
            With Shiro's built-in `roles` filter overridden by aaa-shiro's \
            own ODLRolesAuthorizationFilter, a user without proper \
            permission using Proxy Headers receives a 403 instead of the \
            401 Shiro's default would give - the identity is known, just \
            not the required role.
            """
        )
    )
    def test_insufficient_permissions_with_odl_roles_filter(
        self, allure_step_with_separate_logging, odl_403_roles_filter_settings
    ):
        with allure_step_with_separate_logging("step_send_request"):
            oauth_proxy_header.get_with_proxy_headers(
                PROTECTED_URI,
                username="bob",
                groups="some-other-role",
                expected_code=403,
            )

    @pytest.mark.smoke
    @allure.description(
        textwrap.dedent(
            """
            User using Proxy Headers without the groups field: the parser \
            still authenticates the user (an absent X-Forwarded-Groups header \
            just yields an empty role set, it is not an authentication \
            failure), so this fails the `roles[...]` authorization check the \
            same way the wrong-role case does - a 401 here, not 403, since \
            Shiro's default roles filter is active for this test.
            """
        )
    )
    def test_missing_groups_field(self, allure_step_with_separate_logging):
        with allure_step_with_separate_logging("step_send_request"):
            oauth_proxy_header.get_with_proxy_headers(
                PROTECTED_URI, username="alice", expected_code=401
            )

    @allure.description(
        textwrap.dedent(
            """
            With Shiro's built-in `roles` filter overridden by aaa-shiro's \
            own ODLRolesAuthorizationFilter, a user using Proxy Headers \
            without the groups field receives a 403 instead of the 401 \
            Shiro's default would give - the identity is known, just not \
            the required role.
            """
        )
    )
    def test_missing_groups_field_with_odl_roles_filter(
        self, allure_step_with_separate_logging, odl_403_roles_filter_settings
    ):
        with allure_step_with_separate_logging("step_send_request"):
            oauth_proxy_header.get_with_proxy_headers(
                PROTECTED_URI, username="alice", expected_code=403
            )

    # -----------------------------------------------------------------
    # Missing Identity (HTTP 401)
    # -----------------------------------------------------------------

    @pytest.mark.smoke
    @allure.description("User without any Proxy headers at all receives a 401.")
    def test_missing_all_headers(self, allure_step_with_separate_logging):
        with allure_step_with_separate_logging("step_send_request"):
            oauth_proxy_header.get_with_proxy_headers(PROTECTED_URI, expected_code=401)

    @pytest.mark.smoke
    @allure.description(
        "User using Proxy Headers without the username field receives a 401."
    )
    def test_missing_username_field(self, allure_step_with_separate_logging):
        with allure_step_with_separate_logging("step_send_request"):
            oauth_proxy_header.get_with_proxy_headers(
                PROTECTED_URI, groups=REQUIRED_ROLE, expected_code=401
            )

    # -----------------------------------------------------------------
    # Configuration & Boundary Constraints
    # -----------------------------------------------------------------

    @allure.description(
        textwrap.dedent(
            """
            With max-header-length set to one less than the actual \
            X-Forwarded-Groups value length, that header line is dropped by \
            the parser for being too long (this limit does not apply to \
            X-Forwarded-User at all) rather than rejecting the request \
            outright, so the still-authenticated user ends up with zero \
            roles and fails the `roles[...]` authorization check - \
            confirmed live to come back as a 401 in this environment (see \
            the "Authorization Failures" section above).
            """
        )
    )
    def test_max_header_length_exceeded(
        self, allure_step_with_separate_logging, override_parser_config
    ):
        with allure_step_with_separate_logging("step_set_max_header_length"):
            override_parser_config({"max-header-length": str(len(REQUIRED_ROLE) - 1)})

        with allure_step_with_separate_logging("step_send_request"):
            oauth_proxy_header.get_with_proxy_headers(
                PROTECTED_URI,
                username="alice",
                groups=REQUIRED_ROLE,
                expected_code=401,
            )

    @allure.description(
        textwrap.dedent(
            """
            With max-header-length set to exactly the actual \
            X-Forwarded-Groups value length, the header is accepted rather \
            than dropped for being too long: a 200 - the boundary-exact \
            counterpart to test_max_header_length_exceeded above, confirming \
            the limit is enforced as "longer than" rather than "at least". \
            The rejecting value is applied first and a 401 awaited, so the \
            200 can only come from the new value: the default already \
            accepts this request.
            """
        )
    )
    def test_max_header_length_exact(
        self, allure_step_with_separate_logging, override_parser_config
    ):
        with allure_step_with_separate_logging("step_set_rejecting_max_header_length"):
            override_parser_config({"max-header-length": str(len(REQUIRED_ROLE) - 1)})
        with allure_step_with_separate_logging("step_wait_until_rejected"):
            _wait_until_user_gets_status("alice", REQUIRED_ROLE, 401)

        with allure_step_with_separate_logging("step_set_max_header_length"):
            override_parser_config({"max-header-length": str(len(REQUIRED_ROLE))})
        with allure_step_with_separate_logging("step_wait_until_accepted"):
            _wait_until_user_gets_status("alice", REQUIRED_ROLE, 200)

    @allure.description(
        textwrap.dedent(
            """
            With max-role-length set to one less than REQUIRED_ROLE's own \
            length, that role token is dropped by the parser (not a hard \
            failure), so the still-authenticated user ends up without the \
            required role and fails the `roles[...]` authorization check - \
            confirmed live to come back as a 401 in this environment (see \
            the "Authorization Failures" section above).
            """
        )
    )
    def test_max_role_length_exceeded(
        self, allure_step_with_separate_logging, override_parser_config
    ):
        with allure_step_with_separate_logging("step_set_max_role_length"):
            override_parser_config({"max-role-length": str(len(REQUIRED_ROLE) - 1)})

        with allure_step_with_separate_logging("step_send_request"):
            oauth_proxy_header.get_with_proxy_headers(
                PROTECTED_URI,
                username="alice",
                groups=REQUIRED_ROLE,
                expected_code=401,
            )

    @allure.description(
        textwrap.dedent(
            """
            With max-role-length set to exactly REQUIRED_ROLE's own length, \
            the role is kept rather than dropped for being too long: a 200 \
            - the boundary-exact counterpart to test_max_role_length_exceeded \
            above, confirming the limit is enforced as "longer than" rather \
            than "at least". \
            The rejecting value is applied first and a 401 awaited, so the \
            200 can only come from the new value: the default already \
            accepts this request.
            """
        )
    )
    def test_max_role_length_exact(
        self, allure_step_with_separate_logging, override_parser_config
    ):
        with allure_step_with_separate_logging("step_set_rejecting_max_role_length"):
            override_parser_config({"max-role-length": str(len(REQUIRED_ROLE) - 1)})
        with allure_step_with_separate_logging("step_wait_until_rejected"):
            _wait_until_user_gets_status("alice", REQUIRED_ROLE, 401)

        with allure_step_with_separate_logging("step_set_max_role_length"):
            override_parser_config({"max-role-length": str(len(REQUIRED_ROLE))})
        with allure_step_with_separate_logging("step_wait_until_accepted"):
            _wait_until_user_gets_status("alice", REQUIRED_ROLE, 200)

    @allure.description(
        textwrap.dedent(
            """
            With max-user-length set to one less than the username's own \
            length, the username is rejected by parseUser() itself, which \
            fails authentication outright, so this (unlike the groups-side \
            limits above) still gives a 401.
            """
        )
    )
    def test_max_user_length_exceeded(
        self, allure_step_with_separate_logging, override_parser_config
    ):
        username = "alice"
        with allure_step_with_separate_logging("step_set_max_user_length"):
            override_parser_config({"max-user-length": str(len(username) - 1)})

        with allure_step_with_separate_logging("step_send_request"):
            oauth_proxy_header.get_with_proxy_headers(
                PROTECTED_URI,
                username=username,
                groups=REQUIRED_ROLE,
                expected_code=401,
            )

    @allure.description(
        textwrap.dedent(
            """
            With max-user-length set to exactly the username's own length, \
            parseUser() accepts it rather than rejecting it for being too \
            long: a 200 - the boundary-exact counterpart to \
            test_max_user_length_exceeded above, confirming the limit is \
            enforced as "longer than" rather than "at least". \
            The rejecting value is applied first and a 401 awaited, so the \
            200 can only come from the new value: the default already \
            accepts this request.
            """
        )
    )
    def test_max_user_length_exact(
        self, allure_step_with_separate_logging, override_parser_config
    ):
        username = "alice"
        with allure_step_with_separate_logging("step_set_rejecting_max_user_length"):
            override_parser_config({"max-user-length": str(len(username) - 1)})
        with allure_step_with_separate_logging("step_wait_until_rejected"):
            _wait_until_user_gets_status(username, REQUIRED_ROLE, 401)

        with allure_step_with_separate_logging("step_set_max_user_length"):
            override_parser_config({"max-user-length": str(len(username))})
        with allure_step_with_separate_logging("step_wait_until_accepted"):
            _wait_until_user_gets_status(username, REQUIRED_ROLE, 200)

    @allure.description(
        textwrap.dedent(
            """
            With allowed-chars set to a custom, still-permissive value \
            (letters, digits and hyphen), both a username containing a \
            digit and the required role still validate correctly: a 200 - \
            confirming a custom allowed-chars value doesn't accidentally \
            reject values it was never meant to restrict. A value without \
            digits, which rejects the username, is applied first and a 401 \
            awaited, so the 200 can only come from the new value: the \
            default already accepts this request.
            """
        )
    )
    def test_custom_allowed_chars_accepts_valid_values(
        self, allure_step_with_separate_logging, override_parser_config
    ):
        with allure_step_with_separate_logging("step_set_rejecting_allowed_chars"):
            override_parser_config({"allowed-chars": "[a-zA-Z-]"})
        with allure_step_with_separate_logging("step_wait_until_rejected"):
            _wait_until_user_gets_status("alice1", REQUIRED_ROLE, 401)

        with allure_step_with_separate_logging("step_set_allowed_chars"):
            override_parser_config({"allowed-chars": "[a-zA-Z0-9-]"})
        with allure_step_with_separate_logging("step_wait_until_accepted"):
            _wait_until_user_gets_status("alice1", REQUIRED_ROLE, 200)

    @allure.description(
        textwrap.dedent(
            """
            With allowed-chars restricted to letters and hyphen (so the \
            required role, which itself contains a hyphen, stays valid), a \
            digit in the *username* fails parseUser() itself, which fails \
            authentication outright: a 401.
            """
        )
    )
    def test_character_restriction_violation_username(
        self, allure_step_with_separate_logging, override_parser_config
    ):
        with allure_step_with_separate_logging("step_set_allowed_chars"):
            override_parser_config({"allowed-chars": "[a-zA-Z-]"})

        with allure_step_with_separate_logging("step_send_request"):
            oauth_proxy_header.get_with_proxy_headers(
                PROTECTED_URI,
                username="alice1",
                groups=REQUIRED_ROLE,
                expected_code=401,
            )

    @allure.description(
        textwrap.dedent(
            """
            With allowed-chars restricted to alpha only, a disallowed \
            character in a *role* only is dropped by the parser (the \
            username is unaffected and still authenticates), so the \
            still-authenticated user ends up without the required role and \
            fails the `roles[...]` authorization check - confirmed live to \
            come back as a 401 in this environment (see the "Authorization \
            Failures" section above).
            """
        )
    )
    def test_character_restriction_violation_role(
        self, allure_step_with_separate_logging, override_parser_config
    ):
        with allure_step_with_separate_logging("step_set_allowed_chars"):
            override_parser_config({"allowed-chars": "[a-zA-Z]"})

        with allure_step_with_separate_logging("step_send_request"):
            oauth_proxy_header.get_with_proxy_headers(
                PROTECTED_URI,
                username="alice",
                groups=REQUIRED_ROLE,
                expected_code=401,
            )
