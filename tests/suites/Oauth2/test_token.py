import json
import time

import pytest
import requests

ODL_REALM = "odl-realm"

KEYCLOAC_BASE_URL = "http://localhost:8080"
KEYCLOAK_ADMIN_TOKEN_URL = (
    f"{KEYCLOAC_BASE_URL}/realms/master/protocol/openid-connect/token"
)
KEYCLOAK_USER_TOKEN_URL = (
    f"{KEYCLOAC_BASE_URL}/realms/{ODL_REALM}/protocol/openid-connect/token"
)
KEYCLOAK_CLIENTS_URL = f"{KEYCLOAC_BASE_URL}/admin/realms/{ODL_REALM}/clients"
KEYCLOAK_REALMS_URL = f"{KEYCLOAC_BASE_URL}/admin/realms"
KEYCLOAK_ODL_REALM_URL = f"{KEYCLOAC_BASE_URL}/admin/realms/{ODL_REALM}"
KEYCLOAK_MAPPER_MODELS = f"{KEYCLOAC_BASE_URL}/admin/realms/odl-realm/clients/{{client_uuid}}/protocol-mappers/models"
KEYCLOAK_MAPPER_MODEL = f"{KEYCLOAC_BASE_URL}/admin/realms/odl-realm/clients/{{client_uuid}}/protocol-mappers/models/{{mapper_id}}"
KEYCLOAK_EXPORTED_SETTINGS_PATH = "/home/martin/tmp/keycloak-silicon/realm-export.json"

CLIENT_ID = "odl-application"
AUDIANCE_MAPPER_NAME = "odl-audience"

TOPOLOGY_URL = (
    "http://localhost:8181/rests/data/network-topology:network-topology?content=config"
)
KEY_STORES_URL = (
    "http://localhost:8181/rests/data/aaa-cert-mdsal:key-stores?content=config"
)

ADMIN_USER_NAME = "aadmin"
ADMIN_PASSWORD = "adm1npass"
NON_ADMIN_USER_NAME = "bbasic"
NON_ADMIN_PASSWORD = "bas1cpass"


def get_keycloak_admin_token():
    headers = {"Content-Type": "application/x-www-form-urlencoded"}
    data = {
        "client_id": "admin-cli",
        "username": "admin",
        "password": "admin",
        "grant_type": "password",
    }
    response = requests.post(KEYCLOAK_ADMIN_TOKEN_URL, data=data, headers=headers)
    keycloack_admin_token = response.json()["access_token"]

    return keycloack_admin_token


def get_client_uuid(keycloak_admin_token, client_id):
    headers = {"Authorization": f"Bearer {keycloak_admin_token}"}
    params = {"clientId": client_id}
    response = requests.get(KEYCLOAK_CLIENTS_URL, headers=headers, params=params)
    client_uuid = response.json()[0]["id"]

    return client_uuid


def set_direct_access_for_client():
    keycloak_admin_token = get_keycloak_admin_token()
    client_uuid = get_client_uuid(keycloak_admin_token, client_id=CLIENT_ID)

    headers = {
        "Authorization": f"Bearer {keycloak_admin_token}",
        "Content-Type": "application/json",
    }
    data = {"id": client_uuid, "clientId": CLIENT_ID, "directAccessGrantsEnabled": True}
    response = requests.put(
        KEYCLOAK_CLIENTS_URL + "/" + client_uuid, json=data, headers=headers
    )


def remove_realm_settings(keycloak_admin_token):
    headers = {
        "Authorization": f"Bearer {keycloak_admin_token}",
    }
    requests.delete(KEYCLOAK_ODL_REALM_URL, headers=headers)


def import_realm_settings(keycloak_admin_token):
    headers = {
        "Authorization": f"Bearer {keycloak_admin_token}",
        "Content-Type": "application/json",
    }
    with open(KEYCLOAK_EXPORTED_SETTINGS_PATH) as settings_file:
        data = json.load(settings_file)

    requests.post(KEYCLOAK_REALMS_URL, json=data, headers=headers)


def reset_keycloack_configuration():
    keycloak_admin_token = get_keycloak_admin_token()
    remove_realm_settings(keycloak_admin_token)
    import_realm_settings(keycloak_admin_token)


def get_client_config():
    keycloak_admin_token = get_keycloak_admin_token()
    client_uuid = get_client_uuid(keycloak_admin_token, CLIENT_ID)
    headers = {"Authorization": f"Bearer {keycloak_admin_token}"}
    response = requests.get(f"{KEYCLOAK_CLIENTS_URL}/{client_uuid}", headers=headers)
    return response.json()


def restore_client_config(original_config):
    keycloak_admin_token = get_keycloak_admin_token()
    client_uuid = get_client_uuid(keycloak_admin_token, CLIENT_ID)
    headers = {
        "Authorization": f"Bearer {keycloak_admin_token}",
        "Content-Type": "application/json",
    }
    requests.put(
        f"{KEYCLOAK_CLIENTS_URL}/{client_uuid}", json=original_config, headers=headers
    )


@pytest.fixture(scope="function")
def direct_access_tmp_settings():
    original_state = get_client_config()
    set_direct_access_for_client()
    yield
    set_iss_in_keycloack("")
    restore_client_config(original_state)


def get_user_token(user_name, user_password):
    headers = {"Content-Type": "application/x-www-form-urlencoded"}
    data = {
        "client_id": "odl-application",
        "client_secret": "mysecret",
        "username": user_name,
        "password": user_password,
        "grant_type": "password",
    }
    response = requests.post(KEYCLOAK_USER_TOKEN_URL, data=data, headers=headers)
    user_token = response.json()["access_token"]

    return user_token


def request_endpoint(user_name, user_password, restconf_url):
    user_token = get_user_token(user_name, user_password)

    headers = {"Authorization": f"Bearer {user_token}"}
    response = requests.get(restconf_url, headers=headers)

    return response


def update_token_lifespan(lifespan_duration):
    keycloak_admin_token = get_keycloak_admin_token()
    headers = {
        "Authorization": f"Bearer {keycloak_admin_token}",
        "Content-Type": "application/json",
    }
    payload = {"realm": ODL_REALM, "accessTokenLifespan": lifespan_duration}
    response = requests.put(KEYCLOAK_REALMS_URL, json=payload, headers=headers)


def add_nbf_claim_mapper_in_keycloack(nbf_value):
    keycloak_admin_token = get_keycloak_admin_token()
    headers = {
        "Authorization": f"Bearer {keycloak_admin_token}",
        "Content-Type": "application/json",
    }
    payload = {
        "name": "force-future-nbf",
        "protocol": "openid-connect",
        "protocolMapper": "oidc-hardcoded-claim-mapper",
        "config": {
            "claim.name": "nbf",
            "claim.value": nbf_value,
            "jsonType.label": "long",
            "access.token.claim": "true",
            "id.token.claim": "false",
        },
    }
    client_uuid = get_client_uuid(keycloak_admin_token, CLIENT_ID)
    requests.post(
        KEYCLOAK_MAPPER_MODELS.format(client_uuid=client_uuid),
        json=payload,
        headers=headers,
    )


def get_mapper_id(keycloak_admin_token, mapper_name):
    client_uuid = get_client_uuid(keycloak_admin_token, CLIENT_ID)
    headers = {
        "Authorization": f"Bearer {keycloak_admin_token}",
    }
    response = requests.get(
        KEYCLOAK_MAPPER_MODELS.format(client_uuid=client_uuid), headers=headers
    )
    mappers_list = response.json()
    for mapper in mappers_list:
        if mapper.get("name") == mapper_name:
            return mapper.get("id")

    return None  # Return None if the mapper wasn't found


def set_aud_claim_mapper_in_keycloack(aud_value):
    keycloak_admin_token = get_keycloak_admin_token()
    mapper_id = get_mapper_id(keycloak_admin_token, AUDIANCE_MAPPER_NAME)
    client_uuid = get_client_uuid(keycloak_admin_token, CLIENT_ID)
    headers = {
        "Authorization": f"Bearer {keycloak_admin_token}",
        "Content-Type": "application/json",
    }
    payload = {
        "id": mapper_id,
        "name": "odl-audience",
        "protocol": "openid-connect",
        "protocolMapper": "oidc-audience-mapper",
        "config": {
            "included.client.audience": aud_value,
            "access.token.claim": "true",
            "id.token.claim": "false",
        },
    }
    response = requests.put(
        KEYCLOAK_MAPPER_MODEL.format(client_uuid=client_uuid, mapper_id=mapper_id),
        json=payload,
        headers=headers,
    )


def remove_aud_claim_mapper_in_keycloack():
    keycloak_admin_token = get_keycloak_admin_token()
    mapper_id = get_mapper_id(keycloak_admin_token, AUDIANCE_MAPPER_NAME)
    client_uuid = get_client_uuid(keycloak_admin_token, CLIENT_ID)
    headers = {
        "Authorization": f"Bearer {keycloak_admin_token}",
    }
    requests.delete(
        KEYCLOAK_MAPPER_MODEL.format(client_uuid=client_uuid, mapper_id=mapper_id),
        headers=headers,
    )


def set_iss_in_keycloack(iss_value):
    keycloak_admin_token = get_keycloak_admin_token()
    headers = {
        "Authorization": f"Bearer {keycloak_admin_token}",
        "Content-Type": "application/json",
    }
    payload = {"realm": "odl-realm", "attributes": {"frontendUrl": iss_value}}
    response = requests.put(KEYCLOAK_ODL_REALM_URL, json=payload, headers=headers)


@pytest.mark.parametrize(
    "user_name, user_password, endpoint, expected_status_code",
    [
        (NON_ADMIN_USER_NAME, NON_ADMIN_PASSWORD, TOPOLOGY_URL, 200),
        (NON_ADMIN_USER_NAME, NON_ADMIN_PASSWORD, KEY_STORES_URL, 403),
        (ADMIN_USER_NAME, ADMIN_PASSWORD, KEY_STORES_URL, 200),
        (ADMIN_USER_NAME, ADMIN_PASSWORD, TOPOLOGY_URL, 200),
    ],
)
def test_valid_token(
    direct_access_tmp_settings, user_name, user_password, endpoint, expected_status_code
):
    response = request_endpoint(user_name, user_password, endpoint)
    assert response.status_code == expected_status_code


@pytest.mark.parametrize(
    "nbf, user_name, user_password, endpoint, expected_status_code",
    [
        (
            "2027875600",
            ADMIN_USER_NAME,
            ADMIN_PASSWORD,
            KEY_STORES_URL,
            401,
        ),  # future nbf value
        (
            "1081184921",
            ADMIN_USER_NAME,
            ADMIN_PASSWORD,
            KEY_STORES_URL,
            200,
        ),  # past nbg value
    ],
)
def test_nbf_token(
    direct_access_tmp_settings,
    nbf,
    user_name,
    user_password,
    endpoint,
    expected_status_code,
):
    add_nbf_claim_mapper_in_keycloack(nbf)
    response = request_endpoint(user_name, user_password, endpoint)
    assert response.status_code == expected_status_code


@pytest.mark.parametrize(
    "aud, user_name, user_password, endpoint, expected_status_code",
    [
        (
            "completely-wrong-audience",
            ADMIN_USER_NAME,
            ADMIN_PASSWORD,
            KEY_STORES_URL,
            401,
        ),
    ],
)
def test_custom_aud_token(
    direct_access_tmp_settings,
    aud,
    user_name,
    user_password,
    endpoint,
    expected_status_code,
):
    set_aud_claim_mapper_in_keycloack(aud)
    response = request_endpoint(user_name, user_password, endpoint)
    assert response.status_code == expected_status_code


@pytest.mark.parametrize(
    "user_name, user_password, endpoint",
    [(ADMIN_USER_NAME, ADMIN_PASSWORD, KEY_STORES_URL)],
)
def test_no_aud_token(direct_access_tmp_settings, user_name, user_password, endpoint):
    remove_aud_claim_mapper_in_keycloack()
    response = request_endpoint(user_name, user_password, endpoint)
    assert response.status_code == 401


@pytest.mark.parametrize(
    "iss, user_name, user_password, endpoint, expected_status_code",
    [
        (
            "http://malicious-domain.local/auth",
            ADMIN_USER_NAME,
            ADMIN_PASSWORD,
            KEY_STORES_URL,
            401,
        ),
    ],
)
def test_custom_iss_token(
    direct_access_tmp_settings,
    iss,
    user_name,
    user_password,
    endpoint,
    expected_status_code,
):
    set_iss_in_keycloack(iss)
    response = request_endpoint(user_name, user_password, endpoint)
    assert response.status_code == expected_status_code


"""
@pytest.mark.parametrize(
        "user_name, user_password, endpoint",
        [
            (ADMIN_USER_NAME, ADMIN_PASSWORD, KEY_STORES_URL),
        ]
)
def test_expired_token(direct_access_tmp_settings, user_name, user_password, endpoint):
    update_token_lifespan(5)
    time.sleep(150)
    response = request_endpoint(
        user_name,
        user_password,
        endpoint
    )
    assert response.status_code == 401
    print(response.text)
"""
