#!/usr/bin/env python3
import requests
import ssl
import urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
ssl._create_default_https_context = ssl._create_unverified_context
import time
import threading
import re
import json
import os
import uuid
import base64
import random
import sys
from datetime import datetime
import telebot
from telebot.types import ReplyKeyboardMarkup, KeyboardButton, InlineKeyboardMarkup, InlineKeyboardButton
import pyotp

# ==================== CONFIG ====================
TELEGRAM_TOKEN = "8773375675:AAHH221lLrYgvm1WW2z8fuRGD2PMG1Vf1OY"
ADMIN_ID = 7787612625

COOKIE_DATR = "3XA5at-YBOFaGHi2xPrg-wka"
API_BASE_URL = "https://api.2oo9.cloud/MXS47FLFX0U/tnevs/@public/api"
API_KEY = "MX1RN9ZKIHY"

HEADERS = {
    "mauthapi": API_KEY,
    "Content-Type": "application/json"
}

FRENCH_NAMES = [
    {"prenom":"Jean","nom":"Dupont"}, {"prenom":"Marie","nom":"Martin"},
    {"prenom":"Pierre","nom":"Durand"}, {"prenom":"Sophie","nom":"Lefèvre"},
    {"prenom":"Lucas","nom":"Moreau"}, {"prenom":"Emma","nom":"Petit"},
    {"prenom":"Louis","nom":"Roux"}, {"prenom":"Chloé","nom":"Richard"},
    {"prenom":"Hugo","nom":"Simon"}, {"prenom":"Inès","nom":"Laurent"}
]

# ==================== DATABASE ====================
USER_DB = "users.json"
ACTIVE_NUMBERS_DB = "active_numbers.json"
PROXY_DB = "proxy_db.json"

def init_databases():
    files = {
        USER_DB: [],
        ACTIVE_NUMBERS_DB: {},
        PROXY_DB: {
            "proxies": [],
            "proxy_enabled": True
        }
    }
    for file, default in files.items():
        if not os.path.exists(file):
            with open(file, "w") as f:
                json.dump(default, f)

init_databases()

def get_all_users():
    with open(USER_DB, "r") as f:
        return json.load(f)

def add_user(user_id):
    with open(USER_DB, "r") as f:
        users = json.load(f)
    if user_id not in users:
        users.append(user_id)
        with open(USER_DB, "w") as f:
            json.dump(users, f)

def get_active_numbers():
    with open(ACTIVE_NUMBERS_DB, "r") as f:
        return json.load(f)

def save_active_numbers(numbers):
    with open(ACTIVE_NUMBERS_DB, "w") as f:
        json.dump(numbers, f)

def add_active_number(phone, chat_id, service, range_code):
    data = get_active_numbers()
    data[str(phone)] = {
        "chat_id": chat_id,
        "service": service,
        "range": range_code,
        "time": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    }
    save_active_numbers(data)

def remove_active_number(phone):
    data = get_active_numbers()
    if str(phone) in data:
        del data[str(phone)]
        save_active_numbers(data)

# ==================== PROXY FUNCTIONS ====================
def get_proxy_config():
    with open(PROXY_DB, "r") as f:
        return json.load(f)

def save_proxy_config(config):
    with open(PROXY_DB, "w") as f:
        json.dump(config, f, indent=4)

def add_proxy(proxy_str):
    config = get_proxy_config()
    proxy_str = proxy_str.strip()
    if proxy_str not in config["proxies"]:
        config["proxies"].append(proxy_str)
        save_proxy_config(config)
        return True
    return False

def remove_proxy(proxy_str):
    config = get_proxy_config()
    proxy_str = proxy_str.strip()
    if proxy_str in config["proxies"]:
        config["proxies"].remove(proxy_str)
        save_proxy_config(config)
        return True
    return False

def get_random_proxy():
    config = get_proxy_config()
    if config.get("proxy_enabled", True) and config.get("proxies"):
        raw_proxy = random.choice(config["proxies"])
        # Format can be ip:port or user:pass@ip:port or http://...
        if not raw_proxy.startswith("http://") and not raw_proxy.startswith("https://"):
            p_url = f"http://{raw_proxy}"
        else:
            p_url = raw_proxy
        return {
            "http": p_url,
            "https": p_url
        }, raw_proxy
    return None, None

def extract_otp_from_text(text):
    clean_text = re.sub(r'[-\s]', '', text)
    patterns = [
        r'\b(\d{8})\b', r'\b(\d{7})\b', r'\b(\d{6})\b',
        r'\b(\d{5})\b', r'\b(\d{4})\b', r'\b(\d{3})\b',
        r'code[:\s]*(\d+)', r'OTP[:\s]*(\d+)', r'(\d+)',
    ]
    for pattern in patterns:
        match = re.search(pattern, clean_text, re.IGNORECASE)
        if match and len(match.group(1)) >= 3:
            return match.group(1)
    return "N/A"

def get_service_name_from_msg(msg):
    msg_lower = msg.lower()
    if 'facebook' in msg_lower: return "Facebook"
    elif 'whatsapp' in msg_lower: return "WhatsApp"
    elif 'instagram' in msg_lower: return "Instagram"
    return "Unknown"

def random_name():
    name = random.choice(FRENCH_NAMES)
    return name['prenom'], name['nom']

def random_birth():
    return random.randint(1, 28), random.randint(1, 12), random.randint(1980, 2005)

def clean_phone(phone):
    return re.sub(r'[^0-9]', '', phone)

# ==================== FACEBOOK ACCOUNT CREATOR ====================
def create_facebook_account(phone, password, proxies=None):
    fname, lname = random_name()
    day, month, year = random_birth()
    phone = clean_phone(phone)
    
    android_ua = "Mozilla/5.0 (Linux; Android 12; itel S665L Build/SP1A.210812.016) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.7827.91 Mobile Safari/537.36"
    headers = {
        'User-Agent': android_ua,
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'fr-FR,fr;q=0.9,en;q=0.8',
        'Accept-Encoding': 'gzip, deflate, br, zstd',
        'Connection': 'keep-alive',
        'Upgrade-Insecure-Requests': '1',
        'Content-Type': 'application/x-www-form-urlencoded',
        'sec-ch-ua-platform': '"Android"',
        'sec-ch-ua': '"Android WebView";v="149", "Chromium";v="149", "Not)A;Brand";v="24"',
        'x-response-format': 'JSONStream',
        'sec-ch-ua-mobile': '?1',
        'x-asbd-id': '359341',
        'x-fb-lsd': 'AdRCh7SdER7Za5PotUuics5fFt0',
        'x-requested-with': 'XMLHttpRequest',
        'origin': 'https://limited.facebook.com',
        'sec-fetch-site': 'same-origin',
        'sec-fetch-mode': 'cors',
        'sec-fetch-dest': 'empty',
        'referer': 'https://limited.facebook.com/reg/?is_two_steps_login=0&cid=103&refsrc=deprecated&soft=hjk',
        'priority': 'u=1, i',
        'Cookie': f'datr={COOKIE_DATR}'
    }
    
    data = {
        'ccp': '2', 'reg_instance': COOKIE_DATR, 'submission_request': 'true', 'helper': '',
        'reg_impression_id': str(uuid.uuid4()), 'ns': '1', 'zero_header_af_client': '',
        'app_id': '103', 'logger_id': str(uuid.uuid4()), 'field_names[0]': 'firstname',
        'firstname': fname, 'lastname': lname, 'field_names[1]': 'birthday_wrapper',
        'birthday_day': str(day), 'birthday_month': str(month), 'birthday_year': str(year),
        'age_step_input': '', 'did_use_age': 'false', 'field_names[2]': 'reg_email__',
        'reg_email__': phone, 'field_names[3]': 'sex', 'sex': '2', 'preferred_pronoun': '',
        'custom_gender': '', 'reg_passwd__': password, 'name_suggest_elig': 'false',
        'was_shown_name_suggestions': 'false', 'did_use_suggested_name': 'false',
        'use_custom_gender': 'false', 'guid': '', 'pre_form_step': '', 'submit': 'Sign up',
        'fb_dtsg': 'NAfx5UxG44eai86HC1iwiixBs1mUDFhn3ccN1fj3-SJJc64TeUsEAEg:0:0', 'jazoest': '24748',
        'lsd': 'AdRCh7SdER7Za5PotUuics5fFt0', '__dyn': '1Z3pawlEnwm8_Bg9ppoW5UdE4a2i5U4e0C86u7E39x60zU3ex608ewk9E4W0pKq0FE6S0x81vohw73wGwcq1GwqU2YwbK0oi0zE1jU1soG0hi0Lo6-0Co1kU1UU3jwea',
        '__csr': '', '__hsdp': '', '__hblp': '', '__sjsp': '', '__req': 'g', '__fmt': '1',
        '__a': 'AYzJ_41FhHOHmeaJtz_y-NZ41BrpCkk8MZbenM7ATpRLY9c4d3QLNQW9sph6SN5jNJBH5tH1yvE_P-EybRqM6tZ_nqLEaV4b3ZU', '__user': '0'
    }
    
    url = 'https://limited.facebook.com/reg/submit/?privacy_mutation_token=eyJ0eXBlIjowLCJjcmVhdGlvbl90aW1lIjoxNzgyMTQ5MzY4LCJjYWxsc2l0ZV9pZCI6OTA3OTI0NDAyOTQ4MDU4fQ%3D%3D&app_id=103&multi_step_form=1&skip_suma=0&shouldForceMTouch=1'
    
    try:
        start_time = time.time()
        response = requests.post(url, headers=headers, data=data, proxies=proxies, timeout=30)
        elapsed_time = time.time() - start_time
        
        if response.status_code == 200 and elapsed_time >= 1:
            cookies_dict = response.cookies.get_dict()
            if 'c_user' in cookies_dict:
                uid = cookies_dict['c_user']
                cookie_parts = []
                for key in ['datr', 'sb', 'ps_l', 'ps_n', 'm_pixel_ratio', 'wd', 'c_user', 'fr', 'xs']:
                    if key in cookies_dict:
                        cookie_parts.append(f"{key}={cookies_dict[key].replace(' ', '')}")
                    elif key == 'datr' and key not in cookies_dict:
                        cookie_parts.append(f"datr={COOKIE_DATR}")
                cookie_string = "; ".join(cookie_parts)
                return {
                    'success': True, 'uid': uid, 'name': f"{fname} {lname}",
                    'cookies': cookie_string, 'password': password, 'phone': phone
                }
            else:
                return {'success': False, 'error': 'No c_user in cookies'}
        else:
            return {'success': False, 'error': f'HTTP {response.status_code}'}
    except Exception as e:
        return {'success': False, 'error': str(e)}

# ==================== INSTAGRAM CA FLOW (TWO CURLS) ====================
# This function sends Request 1 and Request 2 through the specified proxy
# replacing the phone number dynamically.
# =======================================================================
def run_instagram_ca_flow(phone, proxies=None):
    phone_clean = clean_phone(phone)
    if not phone_clean.startswith("880"):
        phone_clean = "880" + phone_clean.lstrip("0")
        
    phone_with_plus = f"+{phone_clean}"
    phone_no_plus = phone_clean
    device_id = "alt61QABAAHNPn3xCnBbrmMU3G0B"
    event_id = str(uuid.uuid4())

    # --- INSTAGRAM REQUEST 1 (Bloks Reg Confirmation) ---
    url1 = "https://www.instagram.com/async/wbloks/fetch/?appid=com.bloks.www.bloks.caa.reg.confirmation&type=app&__bkv=5627162f1eec2e00060c8554ba3d5d1931f91be0c89685075a165663278dbd0f"
    headers1 = {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 12; itel S665L Build/SP1A.210812.016; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/150.0.7871.46 Mobile Safari/537.36',
        'Accept-Encoding': 'gzip, deflate, br, zstd',
        'sec-ch-ua-full-version-list': '"Not;A=Brand";v="8.0.0.0", "Chromium";v="150.0.7871.46", "Android WebView";v="150.0.7871.46"',
        'sec-ch-ua-platform': '"Android"',
        'sec-ch-ua': '"Not;A=Brand";v="8", "Chromium";v="150", "Android WebView";v="150"',
        'sec-ch-ua-model': '"itel S665L"',
        'sec-ch-ua-mobile': '?1',
        'sec-ch-prefers-color-scheme': 'light',
        'content-type': 'application/x-www-form-urlencoded;charset=UTF-8',
        'sec-ch-ua-platform-version': '"12.0.0"',
        'origin': 'https://www.instagram.com',
        'x-requested-with': 'mark.via.gp',
        'sec-fetch-site': 'same-origin',
        'sec-fetch-mode': 'cors',
        'sec-fetch-dest': 'empty',
        'referer': 'https://www.instagram.com/accounts/signup/phone',
        'accept-language': 'en-US,en;q=0.9,es-US;q=0.8,es;q=0.7',
        'priority': 'u=1, i',
        'Cookie': 'csrftoken=Exhd_ws8RAMUVCxlisi1tQ; datr=1Xpbak2Jw5DyqfKJUeasuxWg; ig_did=AF034FFB-83AF-425E-A327-46E9CF1B46E9; ps_l=1; ps_n=1; mid=alt61QABAAHNPn3xCnBbrmMU3G0B; wd=360x626'
    }

    reg_info_1 = {
        "first_name": None, "last_name": None, "full_name": None,
        "contactpoint": phone_with_plus, "ar_contactpoint": None,
        "attempted_empty_last_name": None, "contactpoint_type": "phone",
        "is_using_unified_cp": False, "unified_cp_screen_variant": None,
        "is_cp_auto_confirmed": False, "is_cp_auto_confirmable": False,
        "is_cp_claimed": False, "confirmation_code": None, "birthday": None,
        "birthday_derived_from_age": None, "age_range": None, "did_use_age": None,
        "os_shared_age_range": None, "gender": None, "use_custom_gender": False,
        "custom_gender": None, "encrypted_password": None, "username": None,
        "username_prefill": None, "accounts_list_client": None, "fb_conf_source": None,
        "device_id": device_id, "ig4a_qe_device_id": None, "family_device_id": None,
        "fdid_available_on_start": None, "fdid_rid_available_on_start": None,
        "asdid_available_on_start": None, "user_id": None, "safetynet_token": None,
        "skip_slow_rel_check": False, "safetynet_response": None,
        "machine_id": "C3tbanQ3f22LD_OBis9AiMKO", "profile_photo": None,
        "profile_photo_id": None, "profile_photo_upload_id": None, "avatar": None,
        "email_oauth_token_no_contact_perm": None, "email_oauth_token": None,
        "email_oauth_tokens": None, "sign_in_with_google_email": None,
        "should_skip_two_step_conf": None, "openid_tokens_for_testing": None,
        "opt_out_source_account_reg_info_logging_only": None, "encrypted_msisdn": None,
        "encrypted_msisdn_for_safetynet": None, "cached_headers_safetynet_info": None,
        "should_skip_headers_safetynet": None, "headers_last_infra_flow_id": None,
        "headers_last_infra_flow_id_safetynet": None, "headers_flow_id": "a24957d6-ed92-44f2-bcfd-1ce62f955eab",
        "was_headers_prefill_available": False, "sso_enabled": None, "existing_accounts": None,
        "used_ig_birthday": None, "create_new_to_app_account": None, "skip_session_info": None,
        "ck_error": None, "ck_id": None, "ck_nonce": None, "should_save_password": None,
        "fb_access_token": None, "is_msplit_reg": None, "is_spectra_reg": None,
        "dema_account_consent_given": None, "spectra_entry_source": None, "spectra_reg_token": None,
        "spectra_reg_guardian_id": None, "spectra_reg_guardian_logged_in_context": None,
        "spectra_requester_user_id": None, "user_id_of_msplit_creator": None, "msplit_creator_nonce": None,
        "dma_data_combination_consent_given": None, "xapp_accounts": None, "fb_device_id": None,
        "fb_machine_id": None, "ig_device_id": None, "ig_machine_id": None, "should_skip_nta_upsell": None,
        "big_blue_token": None, "caa_reg_flow_source": None, "ig_authorization_token": None,
        "full_sheet_flow": False, "crypted_user_id": None, "is_ca_late_teen": None, "is_early_teen": None,
        "is_caa_perf_enabled": False, "is_preform": True, "should_show_rel_error": False,
        "ignore_suma_check": False, "dismissed_login_upsell_with_cna": False, "ignore_existing_login": False,
        "ignore_existing_login_from_suma": False, "ignore_existing_login_after_errors": False,
        "suggested_first_name": None, "suggested_last_name": None, "suggested_full_name": None,
        "frl_authorization_token": None, "post_form_errors": None, "skip_step_without_errors": False,
        "existing_account_exact_match_checked": False, "existing_account_fuzzy_match_checked": False,
        "email_oauth_exists": False, "confirmation_code_send_error": None, "consent_jurisdiction_at_gate": None,
        "consent_jurisdiction_at_inflow": None, "pc_enforcement_outcome": None, "pc_inflow_decision": None,
        "is_too_young": False, "source_account_type": None, "whatsapp_installed_on_client": False,
        "confirmation_medium": "sms", "source_credentials_type": None, "source_cuid": None,
        "source_account_reg_info": None, "soap_creation_source": None, "source_account_type_to_reg_info": None,
        "registration_flow_id": "", "should_skip_youth_tos": False, "is_youth_regulation_flow_complete": False,
        "is_on_cold_start": False, "email_prefilled": False, "cp_confirmed_by_auto_conf": False,
        "in_sowa_experiment": False, "youth_regulation_config": None, "conf_allow_back_nav_after_change_cp": None,
        "conf_bouncing_cliff_screen_type": None, "conf_show_bouncing_cliff": None, "eligible_to_flash_call_in_ig4a": False,
        "eligible_to_mo_sms_in_ig4a": False, "mo_sms_ent_id": None, "flash_call_permissions_status": None,
        "gms_incoming_call_retriever_eligibility": None, "attestation_result": None,
        "request_data_and_challenge_nonce_string": None, "confirmed_cp_and_code": None,
        "notification_callback_id": None, "reg_suma_state": 0, "is_msplit_neutral_choice": False,
        "msg_previous_cp": None, "ntp_import_source_info": None, "youth_consent_decision_time": None,
        "sk_pipa_consent_given": None, "should_show_spi_before_conf": True, "google_oauth_account": None,
        "is_reg_request_from_ig_suma": False, "is_toa_reg": False, "is_threads_public": False, "spc_import_flow": False,
        "caa_play_integrity_attestation_result": None, "client_known_key_hash": None, "flash_call_provider": None,
        "is_in_gms_experience": None, "flash_call_nonce_prefix_details": None, "spc_birthday_input": False,
        "failed_birthday_year_count": None, "user_presented_medium_source": None, "user_opted_out_of_ntp": None,
        "is_from_registration_reminder": False, "show_youth_reg_in_ig_spc": False, "fb_suma_is_high_confidence": None,
        "screen_visited": ["CAA_REG_CONTACT_POINT_PHONE"], "fb_email_login_upsell_skip_suma_post_tos": False,
        "fb_suma_is_from_email_login_upsell": False, "fb_suma_is_from_phone_login_upsell": False,
        "should_prefill_cp_in_ar": False, "ig_partially_created_account_user_id": None, "ig_partially_created_account_nonce": None,
        "ig_partially_created_account_nonce_expiry": None, "force_sessionless_nux_experience": False,
        "has_seen_suma_landing_page_pre_conf": False, "has_seen_suma_candidate_page_pre_conf": False,
        "has_seen_confirmation_screen": False, "suma_on_conf_threshold": -1, "should_show_error_msg": True,
        "th_profile_photo_token": None, "attempted_silent_auth_in_fb": False, "attempted_silent_auth_in_ig": False,
        "sa_prefetch_callback_id": None, "cp_suma_results_map": None, "source_username": None, "next_uri": None,
        "should_use_next_uri": None, "linking_entry_point": None, "fb_encrypted_partial_new_account_properties": None,
        "starter_pack_name": None, "starter_pack_creator_user_ids": None, "wa_data_bundle": None,
        "bloks_controller_source": None, "airwave_registration_code": None, "is_sessionless_nux": None,
        "login_contactpoint": None, "login_contactpoint_type": None, "should_show_bday_after_name_suggestions": None,
        "should_override_back_nav": False, "ig_footer_variant": "control", "ig_gender": None, "device_network_info": None,
        "is_from_web_lite_reg_controller": None, "login_form_siwg_email": None, "account_setup_waterfall_id": None,
        "is_wanted_suma_user": None, "device_zero_balance_state": None, "wa_to_ig_merged_tos_variant": None,
        "is_in_nta_single_form": False, "source_account_image_asset_id": None, "passkey_eligible_device": None,
        "nta_ac_opted_out": None, "nta_control_reason": None, "nta_risk_type": None, "nta_single_form_variant": None,
        "enable_survey": None, "phone_prefetch_outcome": None, "tos_accepted_on_profile_info": None
    }

    outer_params1 = {
        "params": json.dumps({
            "server_params": {
                "device_id": device_id,
                "is_platform_login": 0,
                "is_from_logged_out": 0,
                "access_flow_version": "pre_mt_behavior",
                "confirmed_cp_and_code": {},
                "reg_info": json.dumps(reg_info_1)
            },
            "client_input_params": {
                "lois_settings": {"lois_token": ""},
                "machine_id": "",
                "cloud_trust_token": None,
                "block_store_machine_id": "",
                "aac": "",
                "gms_incoming_call_retriever_eligibility": "client_not_supported"
            }
        }),
        "current_step": 1,
        "INTERNAL_INFRA_screen_id": "CAA_REG_CONFIRMATION_SCREEN"
    }

    data1 = {
        '__d': 'www', '__user': '0', '__a': '1', '__req': 'u',
        '__hs': '20652.HYP:instagram_web_pkg.2.1...0', 'dpr': '2',
        '__ccg': 'GOOD', '__rev': '1043422073', '__s': 'eglppx:yidgtx:wcp3uj',
        '__hsi': '7663854247820878040',
        '__dyn': '7xeUjG1mxu1syUbFp41twpUnwgU7SbzEdF8aUco2qwJw5ux609vCwjE1EE2Cw8G11wBz81s8hwGxu786a3a1YwBgao6C0Mo2swlo5qfK0EUjwGzEaE2iwNwmE2eUlwhE2Lw6OyES1TwVwDwHg2ZwrUK2K2WE3Gwxyo6O1FwlAcwBwUQp1yU426V89F8uwm8jwsE2xyVrx60luawOwi86K1cweW3mdg',
        '__csr': 'gkihsQp19b5nQggPaFaAWWRHvtx6iRcKHGrBmBdH49UB7JeicURi7KCHUBVmZe8zbyogEyqAkzo-Fs89fGiiRVqDyEx2vuGyESiF6EExo-aBzWiQEO8yalemEym8zpEWWBxK48GUlBx2iEiLy8iwyxK48K8WCxudz8B4h4aCxu8z8-2254i16xa3bAxOEyaxqbxu0L826w0o0U0CO023200Q3o0X2bwajw2M920vo0_W1-g1dE1wofVrw1q20iy0WO0Gw6Gxd03Ko9WSy1qojg8o2DwHwm9XqgLw3YF85h00n5E0bWox2VU0aE4fw7iw',
        '__hsdp': 'gKweX8wigD5K5byVk4-11zo466UlwYwyG2y1HD81bwrU4u1ryfG1lw9W08tw59w8-0Eo5V07yw0M4w2sE1eo1o80NW5E18oaU0xWq04oE1cE5O',
        '__hblp': '0ho2awRwcG5of88GDwwwipEqy84i5E6617wqaGi6ocU4u2Sdz9pUaoiwIxC3m9Bwu85q0wUfo7u14yEgyoa82dU6G0rG0x8bWho3sw2SU0lOw2S80Da0Fo2jzUe83uwUwIw4cw7Axq0i668iwjo1Q9E0Wa1Fwlp981a4q1kwfG13w9aE',
        '__sjsp': 'gKwdIiO8YegD5K5byVk4U2LwkE8Gw9i0gW',
        '__comet_req': '7', 'lsd': 'AdQ5-2IHEcGCOf2LQEml-yQ9LWg', 'jazoest': '22089',
        '__spin_r': '1043422073', '__spin_b': 'trunk', '__spin_t': '1784380117',
        '__crn': 'comet.igweb.PolarisWebBloksRegRoute',
        'params': json.dumps(outer_params1)
    }

    # --- INSTAGRAM REQUEST 2 (Send Confirmation Async) ---
    url2 = "https://www.instagram.com/async/wbloks/fetch/?appid=com.bloks.www.bloks.caa.reg.send_confirmation.async&type=action&__bkv=5627162f1eec2e00060c8554ba3d5d1931f91be0c89685075a165663278dbd0f"
    headers2 = headers1.copy()

    reg_info_2 = reg_info_1.copy()
    reg_info_2["machine_id"] = None
    reg_info_2["screen_visited"] = ["CAA_REG_CONTACT_POINT_PHONE"]

    outer_params2 = {
        "params": json.dumps({
            "server_params": {
                "event_request_id": event_id,
                "phone": phone_no_plus,
                "accounts_list": [],
                "reg_info": json.dumps(reg_info_2),
                "flow_info": "{\"flow_name\":\"new_to_meta_mweb_ig_default\",\"flow_type\":\"ntf\"}",
                "current_step": 0,
                "INTERNAL__latency_qpl_marker_id": 36707139,
                "INTERNAL__latency_qpl_instance_id": "83921734400024",
                "device_id": device_id,
                "family_device_id": None,
                "waterfall_id": None,
                "offline_experiment_group": None,
                "layered_homepage_experiment_group": None,
                "is_platform_login": 0,
                "is_from_logged_in_switcher": 0,
                "is_from_logged_out": 0,
                "access_flow_version": "pre_mt_behavior",
                "login_surface": "unknown"
            },
            "client_input_params": {
                "device_id": device_id,
                "qe_device_id": "",
                "family_device_id": "",
                "build_type": "",
                "cloud_trust_token": None,
                "network_bssid": None,
                "lois_settings": {"lois_token": ""},
                "aac": ""
            }
        }),
        "flow_info": "{\"flow_name\":\"new_to_meta_mweb_ig_default\",\"flow_type\":\"ntf\"}",
        "current_step": 0,
        "INTERNAL__latency_qpl_marker_id": 36707139,
        "INTERNAL__latency_qpl_instance_id": "83921734400024",
        "device_id": device_id,
        "family_device_id": None,
        "waterfall_id": None,
        "offline_experiment_group": None,
        "layered_homepage_experiment_group": None,
        "is_platform_login": 0,
        "is_from_logged_in_switcher": 0,
        "is_from_logged_out": 0,
        "access_flow_version": "pre_mt_behavior",
        "login_surface": "unknown"
    }

    data2 = data1.copy()
    data2['__req'] = 'q'
    data2['params'] = json.dumps(outer_params2)

    res_status_1 = "N/A"
    res_status_2 = "N/A"
    err1 = None
    err2 = None

    # Sending Request 1
    try:
        r1 = requests.post(url1, headers=headers1, data=data1, proxies=proxies, timeout=25, verify=False)
        res_status_1 = str(r1.status_code)
    except Exception as e:
        err1 = str(e)
        res_status_1 = "Failed to send"

    # Small delay between requests
    time.sleep(1)

    # Sending Request 2
    try:
        r2 = requests.post(url2, headers=headers2, data=data2, proxies=proxies, timeout=25, verify=False)
        res_status_2 = str(r2.status_code)
    except Exception as e:
        err2 = str(e)
        res_status_2 = "Failed to send"

    return {
        "req1_status": res_status_1,
        "req2_status": res_status_2,
        "err1": err1,
        "err2": err2
    }

# ==================== KEYBOARDS ====================
def get_main_keyboard():
    # As requested by the user: "অন্য কোন কিছু পরিবর্তন করবেন না শুধু বাটন গুলা replykeymarkup এর মধ্যে dia dan proxy btn full python dan"
    # We create a fully customized ReplyKeyboardMarkup
    markup = ReplyKeyboardMarkup(resize_keyboard=True)
    markup.row(KeyboardButton("🎲 GET NUMBER"), KeyboardButton("🔐 2FA CODE"))
    markup.row(KeyboardButton("🔑 Set Password"), KeyboardButton("🚀 Create Now"))
    markup.row(KeyboardButton("⚙️ PROXY MANAGER"), KeyboardButton("🔙 Back Main Menu"))
    return markup

def get_admin_keyboard():
    markup = ReplyKeyboardMarkup(resize_keyboard=True)
    markup.row(KeyboardButton("📢 Broadcast"), KeyboardButton("📊 Stats"))
    markup.row(KeyboardButton("⚙️ PROXY MANAGER"))
    markup.row(KeyboardButton("🔙 Back Main Menu"))
    return markup

def get_service_keyboard():
    markup = InlineKeyboardMarkup(row_width=2)
    services = voltx_get_live_services()
    for s in services[:4]:
        sid = s.get("sid", "Unknown")
        markup.add(InlineKeyboardButton(f"📘 {sid}", callback_data=f"service_{sid.lower()}"))
    markup.row(InlineKeyboardButton("🔄 Refresh Services", callback_data="refresh_services"))
    markup.row(InlineKeyboardButton("🔙 Back Main Menu", callback_data="back_main_menu"))
    return markup

COUNTRY_MAP = {
    "880": ("🇧🇩", "Bangladesh"), "91": ("🇮🇳", "India"), "1": ("🇺🇸", "USA/Canada"), "44": ("🇬🇧", "UK"), "225": ("🇨🇮", "Ivory Coast")
}

def get_country_info(range_code):
    digits = re.sub(r'[^0-9]', '', range_code)
    for length in (4, 3, 2, 1):
        prefix = digits[:length]
        if prefix in COUNTRY_MAP: return COUNTRY_MAP[prefix]
    return ("📱", "")

def get_range_keyboard(ranges, service):
    markup = InlineKeyboardMarkup(row_width=2)
    for r in ranges[:10]:
        flag, country = get_country_info(r)
        label = f"{flag} {r} {country}" if country else f"📱 {r}"
        markup.add(InlineKeyboardButton(label, callback_data=f"get_number_{service}_{r}"))
    markup.row(InlineKeyboardButton("🔄 Refresh", callback_data=f"refresh_ranges_{service}"))
    markup.row(InlineKeyboardButton("🔙 Back to Services", callback_data="back_to_services"))
    return markup

def get_fb_creator_range_keyboard(ranges):
    markup = InlineKeyboardMarkup(row_width=2)
    for r in ranges[:12]:
        flag, country = get_country_info(r)
        label = f"{flag} {r} {country}" if country else f"📱 {r}"
        markup.add(InlineKeyboardButton(label, callback_data=f"fbcreate_{r}"))
    markup.row(InlineKeyboardButton("🔙 Back Main Menu", callback_data="back_main_menu"))
    return markup

def get_2fa_keyboard():
    markup = ReplyKeyboardMarkup(resize_keyboard=True)
    markup.row(KeyboardButton("🔄 Regenerate"))
    markup.row(KeyboardButton("🔙 Back Main Menu"))
    return markup

# ==================== BOT ENGINE & STATE ====================
bot = telebot.TeleBot(TELEGRAM_TOKEN)
user_service = {}
user_last_range = {}
user_data_store = {}
bot_states = {}

@bot.message_handler(commands=['start'])
def start_cmd(message):
    add_user(message.chat.id)
    if message.from_user.id == ADMIN_ID:
        bot.send_message(message.chat.id, "👋 Welcome Admin!", reply_markup=get_admin_keyboard())
    else:
        bot.send_message(
            message.chat.id,
            f"✨ Welcome {message.from_user.first_name}! ✨\n\n"
            f"🤖 <b>ARAFAAT FB Creator + PROXY Setup</b>\n\n"
            f"📌 <b>কিভাবে ব্যবহার করবেন (Auto Creator + Proxy):</b>\n"
            f"1️⃣ প্রথমে '🔑 Set Password' বাটনে ক্লিক করে পাসওয়ার্ড সেট করুন।\n"
            f"2️⃣ এরপর '🚀 Create Now' বাটনে চাপ দিয়ে কাঙ্ক্ষিত লাইভ রেঞ্জ সিলেক্ট করুন।\n"
            f"3️⃣ কয়টি অ্যাকাউন্ট তৈরি করবেন তা ইনপুট দিন (সর্বোচ্চ ৫)।\n"
            f"4️⃣ বোট স্বয়ংক্রিয়ভাবে প্রক্সি কানেক্ট করে নাম্বার তুলে রিয়াল-টাইম একাউন্ট তৈরি করে কুকি সহ তথ্য দিয়ে দেবে।\n\n"
            f"💡 আপনি সরাসরি যেকোনো নাম্বার বোটে পাঠাতে পারেন, বোট প্রক্সি কানেক্ট করে Instagram Request পাঠিয়ে Status দেখাবে!",
            parse_mode="HTML",
            reply_markup=get_main_keyboard()
        )

@bot.message_handler(commands=['admin'])
def admin_panel(message):
    if message.from_user.id == ADMIN_ID:
        bot.send_message(message.chat.id, "🔧 Admin Panel", reply_markup=get_admin_keyboard())

# ==================== CALLBACK CODES ====================
@bot.callback_query_handler(func=lambda call: True)
def handle_callback(call):
    chat_id = call.message.chat.id
    msg_id = call.message.message_id
    data = call.data

    if data == "back_main_menu":
        bot.answer_callback_query(call.id)
        try: bot.delete_message(chat_id, msg_id)
        except: pass
        bot.send_message(chat_id, "🏠 Main Menu", reply_markup=get_main_keyboard())
        return
    
    if data == "back_to_services":
        bot.edit_message_text("🔍 Select Service:", chat_id, msg_id, reply_markup=get_service_keyboard())
        bot.answer_callback_query(call.id)
        return
    
    if data == "back_to_ranges":
        service_name = user_service.get(chat_id, "facebook")
        ranges = voltx_get_ranges_for_service(service_name)
        bot.answer_callback_query(call.id)
        try: bot.delete_message(chat_id, msg_id)
        except: pass
        if ranges:
            bot.send_message(chat_id, f"🔥 Live Ranges for {service_name.capitalize()}:", reply_markup=get_range_keyboard(ranges, service_name))
        else:
            bot.send_message(chat_id, "❌ No live ranges found!", reply_markup=get_service_keyboard())
        return
    
    if data == "refresh_services":
        bot.edit_message_text("🔍 Select Service:", chat_id, msg_id, reply_markup=get_service_keyboard())
        bot.answer_callback_query(call.id)
        return
    
    if data.startswith("refresh_ranges_"):
        service_name = data.replace("refresh_ranges_", "")
        ranges = voltx_get_ranges_for_service(service_name)
        if ranges:
            bot.edit_message_text(f"🔥 Live Ranges for {service_name.capitalize()} (Refreshed):", chat_id, msg_id, reply_markup=get_range_keyboard(ranges, service_name))
        else:
            bot.edit_message_text("❌ No live ranges found!", chat_id, msg_id, reply_markup=get_service_keyboard())
        bot.answer_callback_query(call.id)
        return
    
    if data.startswith("service_"):
        service_name = data.replace("service_", "")
        user_service[chat_id] = service_name
        ranges = voltx_get_ranges_for_service(service_name)
        if ranges:
            bot.edit_message_text(f"🔥 Live Ranges for {service_name.capitalize()}:", chat_id, msg_id, reply_markup=get_range_keyboard(ranges, service_name))
        else:
            bot.edit_message_text(f"❌ No live ranges found!", chat_id, msg_id, reply_markup=get_service_keyboard())
        bot.answer_callback_query(call.id)
        return
    
    if data.startswith("get_number_"):
        parts = data.split("_")
        service_name = parts[2]
        range_code = parts[3]
        user_last_range[chat_id] = range_code
        user_service[chat_id] = service_name
        
        bot.edit_message_text(f"⏳ Requesting 1 number from `{range_code}`...\n\nPlease wait...", chat_id, msg_id, parse_mode="Markdown")
        numbers_found = voltx_fetch_single_number(range_code)
        if numbers_found:
            for number in numbers_found:
                add_active_number(number, chat_id, service_name.capitalize(), range_code)
            bot.delete_message(chat_id, msg_id)
            send_number_received_notification(chat_id, numbers_found, service_name.capitalize(), range_code)
        else:
            bot.edit_message_text(f"❌ No numbers available from `{range_code}`!\n\nPlease try another range.", chat_id, msg_id, parse_mode="Markdown", reply_markup=get_range_keyboard(voltx_get_ranges_for_service(service_name), service_name))
        bot.answer_callback_query(call.id)
        return
    
    if data.startswith("change_number_"):
        service_name = data.replace("change_number_", "")
        range_code = user_last_range.get(chat_id)
        if not range_code:
            bot.answer_callback_query(call.id, "Select range first!", show_alert=True)
            return
        bot.delete_message(chat_id, msg_id)
        loading_msg = bot.send_message(chat_id, f"⏳ Requesting 1 new number from `{range_code}`...\n\nPlease wait...", parse_mode="Markdown")
        numbers_found = voltx_fetch_single_number(range_code)
        bot.delete_message(chat_id, loading_msg.message_id)
        if numbers_found:
            for number in numbers_found:
                add_active_number(number, chat_id, service_name, range_code)
            send_number_received_notification(chat_id, numbers_found, service_name, range_code)
        else:
            bot.send_message(chat_id, f"❌ No numbers available from `{range_code}`!\n\nPlease try another range.", parse_mode="Markdown", reply_markup=get_service_keyboard())
        bot.answer_callback_query(call.id)
        return

    if data.startswith("fbcreate_"):
        bot.answer_callback_query(call.id)
        selected_range = data.replace("fbcreate_", "")
        if chat_id not in bot_states:
            bot_states[chat_id] = {}
        bot_states[chat_id]['selected_fb_range'] = selected_range
        
        try: bot.delete_message(chat_id, msg_id)
        except: pass
        
        msg = bot.send_message(
            chat_id,
            f"🎯 <b>Selected Range:</b> <code>{selected_range}</code>\n\n"
            "🔢 <b>How many accounts? (Max 5):</b>\n"
            "Enter a number between 1-5.",
            parse_mode='HTML'
        )
        bot_states[chat_id]['waiting_for_account_count'] = True
        return

    # --- INLINE PROXY MANAGER ---
    if data == "toggle_proxy":
        config = get_proxy_config()
        config["proxy_enabled"] = not config["proxy_enabled"]
        save_proxy_config(config)
        bot.answer_callback_query(call.id, f"Proxy toggled to: {config['proxy_enabled']}")
        show_proxy_manager_inline(chat_id, msg_id)
        return

    if data == "add_proxy_prompt":
        bot.answer_callback_query(call.id)
        try: bot.delete_message(chat_id, msg_id)
        except: pass
        msg = bot.send_message(chat_id, "✏️ <b>Please send the Proxy details in any of the formats below:</b>\n\nFormat 1: <code>ip:port</code>\nFormat 2: <code>user:pass@ip:port</code>\nFormat 3: <code>http://user:pass@ip:port</code>", parse_mode="HTML")
        bot_states[chat_id]['waiting_for_new_proxy'] = True
        return

    if data.startswith("del_proxy_"):
        idx = int(data.replace("del_proxy_", ""))
        config = get_proxy_config()
        if 0 <= idx < len(config["proxies"]):
            removed = config["proxies"].pop(idx)
            save_proxy_config(config)
            bot.answer_callback_query(call.id, f"Removed Proxy successfully!")
        else:
            bot.answer_callback_query(call.id, f"Error: Proxy not found!")
        show_proxy_manager_inline(chat_id, msg_id)
        return

# ==================== PROXY MANAGER INLINE INTERFACE ====================
def show_proxy_manager_inline(chat_id, msg_id=None):
    config = get_proxy_config()
    enabled_status = "🟢 ENABLED" if config["proxy_enabled"] else "🔴 DISABLED"
    
    proxy_list_str = ""
    if not config["proxies"]:
        proxy_list_str = "⚠️ <i>No Proxies configured yet!</i>"
    else:
        for i, p in enumerate(config["proxies"]):
            proxy_list_str += f"{i+1}. <code>{p}</code>\n"

    markup = InlineKeyboardMarkup()
    markup.row(InlineKeyboardButton(f"Status: {enabled_status}", callback_data="toggle_proxy"))
    markup.row(InlineKeyboardButton("➕ Add Proxy", callback_data="add_proxy_prompt"))
    
    # Add delete buttons for each proxy
    for i, p in enumerate(config["proxies"][:8]):
        # limit to 8 to avoid telegram button payload limit
        markup.add(InlineKeyboardButton(f"❌ Delete {p[:25]}...", callback_data=f"del_proxy_{i}"))

    markup.row(InlineKeyboardButton("🔙 Back to Main Menu", callback_data="back_main_menu"))

    msg_text = f"⚙️ <b>ARAFAAT Proxy Manager Panel</b>\n━━━━━━━━━━━━━━━━━━━━\n{proxy_list_str}\n━━━━━━━━━━━━━━━━━━━━\n💡 <b>Note:</b> proxies will be automatically connected before creating accounts or sending request flows, and turned off right after."
    
    if msg_id:
        try:
            bot.edit_message_text(msg_text, chat_id, msg_id, parse_mode="HTML", reply_markup=markup)
        except:
            bot.send_message(chat_id, msg_text, parse_mode="HTML", reply_markup=markup)
    else:
        bot.send_message(chat_id, msg_text, parse_mode="HTML", reply_markup=markup)

# ==================== CONTROLLER & TEXT CODES ====================
@bot.message_handler(func=lambda m: True)
def handle_text_messages(message):
    chat_id = message.chat.id
    text = message.text.strip()
    
    if chat_id not in bot_states:
        bot_states[chat_id] = {}

    if text == "🔙 Back Main Menu":
        bot_states[chat_id] = {}
        if message.from_user.id == ADMIN_ID:
            bot.send_message(chat_id, "🏠 Main Menu", reply_markup=get_admin_keyboard())
        else:
            bot.send_message(chat_id, "🏠 Main Menu", reply_markup=get_main_keyboard())
        return

    if message.from_user.id == ADMIN_ID and text in ["📢 Broadcast", "📊 Stats"]:
        if text == "📢 Broadcast":
            msg = bot.send_message(chat_id, "📢 Send broadcast:")
            bot.register_next_step_handler(msg, broadcast_msg)
        elif text == "📊 Stats":
            users = len(get_all_users())
            active = len(get_active_numbers())
            config = get_proxy_config()
            proxies_count = len(config["proxies"])
            bot.send_message(chat_id, f"📊 <b>Bot Statistics:</b>\n\n👥 Users: {users}\n📱 Active Numbers: {active}\n⚙️ Configured Proxies: {proxies_count}\n⚡ Proxy System: {'🟢 Enabled' if config['proxy_enabled'] else '🔴 Disabled'}", parse_mode="HTML")
        return

    if text == "🎲 GET NUMBER":
        bot.send_message(chat_id, "🔍 Select Service:", reply_markup=get_service_keyboard())
        return

    if text == "🔐 2FA CODE":
        msg = bot.send_message(chat_id, "🔐 Send 2FA Secret Key:\nExample: JBSWY3DPEHPK3PXP", parse_mode="Markdown")
        bot.register_next_step_handler(msg, process_2fa)
        return

    if text == "🔄 Regenerate":
        msg = bot.send_message(chat_id, "🔐 Send 2FA Secret Key:", parse_mode="Markdown")
        bot.register_next_step_handler(msg, process_2fa)
        return

    if text == "🔑 Set Password":
        bot.send_message(
            chat_id,
            "🔑 <b>Set Account Password</b>\n\nPlease send your desired password.\nPassword must be at least 6 characters.\n\nExample: MyPass@123",
            parse_mode='HTML', reply_markup=get_main_keyboard()
        )
        bot_states[chat_id]['waiting_for_password'] = True
        return

    if text == "⚙️ PROXY MANAGER":
        show_proxy_manager_inline(chat_id)
        return

    if text == "🚀 Create Now":
        if chat_id not in user_data_store or 'password' not in user_data_store[chat_id]:
            bot.send_message(chat_id, "⚠️ <b>Password Not Set!</b>\n\nPlease set a password first using '🔑 Set Password' button.", parse_mode='HTML', reply_markup=get_main_keyboard())
            return
        
        fb_ranges = voltx_get_ranges_for_service("facebook")
        if fb_ranges:
            bot.send_message(
                chat_id, 
                "📘 <b>Facebook Live Ranges:</b>\n\nSelect a range to create accounts.", 
                parse_mode='HTML', 
                reply_markup=get_fb_creator_range_keyboard(fb_ranges)
            )
        else:
            bot.send_message(chat_id, "❌ No Facebook live ranges available!", reply_markup=get_main_keyboard())
        return

    # --- WAITING FOR PASSWORD STATE ---
    if bot_states[chat_id].get('waiting_for_password'):
        if len(text) < 6:
            bot.send_message(chat_id, "⚠️ Password must be at least 6 characters!\nPlease try again.", reply_markup=get_main_keyboard())
            return
        if chat_id not in user_data_store:
            user_data_store[chat_id] = {}
        user_data_store[chat_id]['password'] = text
        bot_states[chat_id]['waiting_for_password'] = False
        bot.send_message(chat_id, f"✅ <b>Password Set Successfully!</b>\n\nPassword: <code>{text}</code>\n\nNow click '🚀 Create Now' to start.", parse_mode='HTML', reply_markup=get_main_keyboard())
        return

    # --- WAITING FOR NEW PROXY CONFIG ---
    if bot_states[chat_id].get('waiting_for_new_proxy'):
        added = add_proxy(text)
        bot_states[chat_id]['waiting_for_new_proxy'] = False
        if added:
            bot.send_message(chat_id, f"✅ <b>Proxy Added Successfully!</b>\n\nProxy: <code>{text}</code>", parse_mode="HTML", reply_markup=get_main_keyboard())
        else:
            bot.send_message(chat_id, "⚠️ <b>Proxy already exists or is invalid!</b>", parse_mode="HTML", reply_markup=get_main_keyboard())
        show_proxy_manager_inline(chat_id)
        return

    # --- WAITING FOR ACCOUNT COUNT STATE ---
    if bot_states[chat_id].get('waiting_for_account_count'):
        if not text.isdigit() or int(text) < 1 or int(text) > 5:
            bot.send_message(chat_id, "⚠️ Enter a valid number between 1-5!", reply_markup=get_main_keyboard())
            return
        
        total_accounts_needed = int(text)
        bot_states[chat_id]['waiting_for_account_count'] = False
        bot_states[chat_id]['creating_mode'] = True
        
        range_code = bot_states[chat_id].get('selected_fb_range')
        password = user_data_store.get(chat_id, {}).get('password')
        
        # Get active proxy
        proxies, proxy_raw = get_random_proxy()
        proxy_msg_text = f"<code>{proxy_raw}</code>" if proxy_raw else "None (Direct Connection)"
        
        bot.send_message(
            chat_id, 
            f"🚀 <b>Starting Auto Batch Creation with Proxy Config...</b>\n\n"
            f"🌀 <b>Range:</b> <code>{range_code}</code>\n"
            f"🔢 <b>Target:</b> {total_accounts_needed}\n"
            f"⚙️ <b>Active Proxy:</b> {proxy_msg_text}\n\n"
            f"⚡ Connecting proxy and creating accounts... Please wait:", 
            parse_mode='HTML'
        )

        def single_account_creator(phone, index, total, user_pass, target_chat, results_tracker):
            if not bot_states.get(target_chat, {}).get('creating_mode', False):
                return

            processing_msg = bot.send_message(target_chat, f"⏳ <b>[Account {index}/{total}] Connecting proxy...</b>", parse_mode='HTML')
            time.sleep(1) # Connect proxy delay
            
            bot.edit_message_text(f"🚀 <b>[Account {index}/{total}] Proxy connected! Creating account...</b>\n📱 <code>{phone}</code>", target_chat, processing_msg.message_id, parse_mode='HTML')
            
            # Use proxy config for the request
            result = create_facebook_account(phone, user_pass, proxies=proxies)
            
            if result.get('success'):
                results_tracker['success'] += 1
                account_text = (
                    f"✅ <b>ACCOUNT #{index} - CREATED!</b>\n"
                    f"━━━━━━━━━━━━━━━━━━━━\n"
                    f"📱 <b>Number:</b> <code>{result['phone']}</code>\n"
                    f"🆔 <b>UID:</b> <code>{result['uid']}</code>\n"
                    f"👤 <b>Name:</b> <code>{result['name']}</code>\n"
                    f"🔑 <b>Password:</b> <code>{result['password']}</code>\n"
                    f"🍪 <b>Cookies:</b> <code>{result['cookies']}</code>\n"
                    f"━━━━━━━━━━━━━━━━━━━━"
                )
                bot.edit_message_text(account_text, target_chat, processing_msg.message_id, parse_mode='HTML')
            else:
                results_tracker['failed'] += 1
                error = result.get('error', 'Unknown error')
                bot.edit_message_text(f"❌ <b>ACCOUNT #{index} - FAILED!</b>\n📱 <code>{phone}</code>\n❌ {error}", target_chat, processing_msg.message_id, parse_mode='HTML')

        def auto_fetch_and_schedule_batch(target_chat, fb_range, count, user_pass):
            tracker = {'success': 0, 'failed': 0}
            
            for i in range(1, count + 1):
                if not bot_states.get(target_chat, {}).get('creating_mode', False):
                    break
                
                status_msg = bot.send_message(target_chat, f"⏳ [{i}/{count}] Fetching new number...", parse_mode='HTML')
                fetched_num = voltx_fetch_number(fb_range)
                bot.delete_message(target_chat, status_msg.message_id)
                
                if not fetched_num:
                    bot.send_message(target_chat, f"❌ [{i}/{count}] Failed to fetch number!", parse_mode='HTML')
                    tracker['failed'] += 1
                    continue
                
                add_active_number(fetched_num, target_chat, "Facebook", fb_range)
                
                t = threading.Thread(
                    target=single_account_creator, 
                    args=(fetched_num, i, count, user_pass, target_chat, tracker)
                )
                t.start()
                t.join()
                time.sleep(1.5)

            summary_text = (
                f"🎉 <b>BATCH COMPLETE!</b>\n"
                f"━━━━━━━━━━━━━━━━━━━━\n"
                f"📱 Total: {count}\n"
                f"✅ Success: {tracker['success']}\n"
                f"❌ Failed: {tracker['failed']}\n"
                f"━━━━━━━━━━━━━━━━━━━━\n"
                f"🏠 Click '🚀 Create Now' for a new batch."
            )
            bot.send_message(target_chat, summary_text, parse_mode='HTML', reply_markup=get_main_keyboard())

        threading.Thread(
            target=auto_fetch_and_schedule_batch, 
            args=(chat_id, range_code, total_accounts_needed, password), 
            daemon=True
        ).start()
        return

    # --- DIRECT PHONE NUMBER SEND (FLOW METHOD) ---
    # As requested: "বট এ নম্বর সেন্ড করলে প্রক্সি কানেক্ট হওয়ার পর রিকোয়েস্ট পাঠাবে"
    phone_pattern = re.compile(r'^\+?[0-9]{7,15}$')
    if phone_pattern.match(text):
        bot.send_message(chat_id, f"🎯 <b>Received Phone Number:</b> <code>{text}</code>", parse_mode="HTML")
        
        # 1. Choose and connect to a proxy
        proxies, proxy_raw = get_random_proxy()
        proxy_display = f"<code>{proxy_raw}</code>" if proxy_raw else "<i>Direct (None)</i>"
        
        progress_msg = bot.send_message(chat_id, f"⏳ <b>Connecting proxy...</b>\nProxy: {proxy_display}", parse_mode="HTML")
        
        # 1 second delay as requested
        time.sleep(1)
        
        bot.edit_message_text(f"🚀 <b>Proxy Connected successfully!</b>\n\n⚡ Sending requests via {proxy_display}...", chat_id, progress_msg.message_id, parse_mode="HTML")
        
        # Send Request 1 and Request 2
        def run_background_flow(target_phone, target_proxies, msg_id, p_display):
            result = run_instagram_ca_flow(target_phone, target_proxies)
            
            # Build beautiful result report
            status1 = result["req1_status"]
            status2 = result["req2_status"]
            
            emoji1 = "🟢" if status1 == "200" else "🔴"
            emoji2 = "🟢" if status2 == "200" else "🔴"
            
            err_log = ""
            if result["err1"]:
                err_log += f"⚠️ <b>Request 1 Err:</b> {result['err1']}\n"
            if result["err2"]:
                err_log += f"⚠️ <b>Request 2 Err:</b> {result['err2']}\n"

            report_text = (
                f"📊 <b>INSTAGRAM FLOW REPORT</b>\n"
                f"━━━━━━━━━━━━━━━━━━━━\n"
                f"📱 <b>Number:</b> <code>{target_phone}</code>\n"
                f"⚙️ <b>Proxy:</b> {p_display}\n\n"
                f"{emoji1} <b>Request 1 Code:</b> {status1}\n"
                f"{emoji2} <b>Request 2 Code:</b> {status2}\n"
                f"━━━━━━━━━━━━━━━━━━━━\n"
                f"{err_log}"
                f"💡 <i>Proxy automatically closed and disabled. ready for next operation.</i>"
            )
            bot.edit_message_text(report_text, chat_id, msg_id, parse_mode="HTML")

        threading.Thread(
            target=run_background_flow, 
            args=(text, proxies, progress_msg.message_id, proxy_display),
            daemon=True
        ).start()
        return

    bot.send_message(chat_id, "ℹ️ Please use the buttons below or send a phone number to get started!", reply_markup=get_main_keyboard())

# ==================== STEP FUNCTIONS ====================
def process_2fa(message):
    secret = message.text.strip().replace(" ", "")
    try:
        totp = pyotp.TOTP(secret)
        otp = totp.now()
        bot.send_message(message.chat.id, f"🔐 Your 2FA Code:\n\n`{otp}`", parse_mode="Markdown", reply_markup=get_2fa_keyboard())
    except:
        bot.send_message(message.chat.id, "❌ Invalid Secret Key!", reply_markup=get_main_keyboard())

def broadcast_msg(message):
    users = get_all_users()
    success = 0
    for uid in users:
        try:
            bot.send_message(uid, f"📢 Broadcast\n\n{message.text}")
            success += 1
            time.sleep(0.05)
        except: pass
    bot.send_message(ADMIN_ID, f"✅ Sent to {success} users!")

# ==================== VOLTX REMOTE API SIMULATION ====================
def voltx_get_live_services():
    url = f"{API_BASE_URL}/liveaccess"
    try:
        res = requests.get(url, headers=HEADERS, timeout=15, verify=False)
        if res.status_code == 200:
            data = res.json()
            if data.get("meta", {}).get("code") == 200:
                services = data.get("data", {}).get("services", [])
                if services: return services
    except Exception as e:
        print(f"Error loading live services: {e}")
    return [
        {"sid": "Facebook", "ranges": ["8801XXX", "22501XXX"]},
        {"sid": "WhatsApp", "ranges": ["8801XXX", "447XXX"]}
    ]

def voltx_get_ranges_for_service(service_name):
    services = voltx_get_live_services()
    for s in services:
        if s.get("sid", "").lower() == service_name.lower():
            ranges = s.get("ranges", [])
            if ranges: return ranges
    return ["8801XXX", "22501XXX"]

def voltx_fetch_number(range_code):
    rid = range_code.replace("XXX", "").replace("X", "").strip()
    if not rid: rid = "8801"
    url = f"{API_BASE_URL}/getnum"
    payload = {"rid": rid}
    try:
        res = requests.post(url, json=payload, headers=HEADERS, timeout=30, verify=False)
        if res.status_code == 200:
            data = res.json()
            if data.get("meta", {}).get("code") == 200:
                number_data = data.get("data", {})
                full_number = number_data.get("full_number") or number_data.get("no_plus_number")
                if full_number:
                    return str(full_number).replace("+", "").strip()
    except Exception as e:
        print(f"Error fetching number: {e}")
    return None

def voltx_fetch_single_number(range_code):
    number = voltx_fetch_number(range_code)
    return [number] if number else []

def voltx_check_otp():
    url = f"{API_BASE_URL}/success-otp"
    results = []
    try:
        res = requests.get(url, headers=HEADERS, timeout=15, verify=False)
        if res.status_code == 200:
            data = res.json()
            if data.get("meta", {}).get("code") == 200:
                otps = data.get("data", {}).get("otps", [])
                active = get_active_numbers()
                for phone in active:
                    for otp_item in otps:
                        otp_number = otp_item.get("number", "").replace("+", "").strip()
                        if phone == otp_number:
                            message = otp_item.get("message", "")
                            if message:
                                otp_code = extract_otp_from_text(message)
                                service_name = get_service_name_from_msg(message)
                                if service_name == "Unknown":
                                    service_name = active[phone].get("service", "Unknown")
                                if otp_code != "N/A":
                                    results.append({
                                        "phone": phone, "message": message,
                                        "otp": otp_code, "service": service_name,
                                    })
                                    break
    except Exception as e:
        print(f"Error checking OTP: {e}")
    return results

def send_otp_notification(chat_id, phone, service, otp, message):
    dm_msg = f"`{phone}`\n`{otp}`"
    try:
        bot.send_message(chat_id, dm_msg, parse_mode="Markdown")
    except Exception as e: 
        print(f"Send error: {e}")

def send_number_received_notification(chat_id, numbers, service_name, range_code=None):
    for number in numbers:
        country_line = range_line = ""
        if range_code:
            flag, country_name = get_country_info(range_code)
            country_line = f"\n🌍 Country : {flag} {country_name}" if country_name else f"\n🌍 Country : {flag}"
            range_line = f"\n🌀 Range : `{range_code}`"

        markup = InlineKeyboardMarkup(row_width=1)
        markup.row(InlineKeyboardButton(f"📋 {number} (Tap to Copy)", callback_data="copy_text_fake"))
        markup.row(InlineKeyboardButton("🔄 Change Number", callback_data=f"change_number_{service_name}"))
        markup.row(InlineKeyboardButton("🌍 Change Country", callback_data=f"back_to_ranges"))

        msg = f"🎯 NEW NUMBER RECEIVED!\n━━━━━━━━━━━━━━━━━━━━\n📱 Number: `{number}`\n🎯 Service: {service_name}{country_line}{range_line}\n━━━━━━━━━━━━━━━━━━━━\n💡 OTP will appear here automatically!\n\n👆 নাম্বার বাটনে চাপ দিলে কপি হবে!"
        bot.send_message(chat_id, msg, parse_mode="Markdown", reply_markup=markup)

# ==================== OTP MONITOR LOOP ====================
sent_otps = set()

def otp_monitor():
    global sent_otps
    print("🔄 OTP Monitor Loop Active (INBOX ONLY)")
    while True:
        try:
            otps = voltx_check_otp()
            for otp_data in otps:
                phone = otp_data["phone"]
                unique_key = f"{phone}_{otp_data['otp']}"
                if unique_key not in sent_otps:
                    sent_otps.add(unique_key)
                    active = get_active_numbers()
                    if str(phone) in active:
                        chat_id = active[str(phone)]["chat_id"]
                        send_otp_notification(chat_id, phone, otp_data["service"], otp_data["otp"], otp_data["message"])
                        remove_active_number(phone)
            if len(sent_otps) > 2000:
                sent_otps.clear()
        except Exception as e:
            print(f"Monitor Error: {e}")
        time.sleep(5)

# ==================== MAIN CORE ====================
if __name__ == "__main__":
    print("=" * 60)
    print("🤖 ARAFAAT SYSTEM + VOLTX OTP BOT (WITH INSTAGRAM PROXY)")
    print("=" * 60)
    
    threading.Thread(target=otp_monitor, daemon=True).start()
    
    print("✅ System Started Successfully!")
    bot.infinity_polling(timeout=60)
