import os
import json
import logging
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly.controllers import android_device

PHONE_PACKAGE = "com.m3u.smartphone"
PHONE_MAIN_ACTIVITY = "com.m3u.smartphone/.MainActivity"
TV_PACKAGE = "com.m3u.tv"
TV_LAUNCH_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER"
SETTING_DIRECT_TV_HOST = "m3u_remote_control_tv_host"
SETTING_DIRECT_TV_PORT = "m3u_remote_control_tv_port"
SETTING_TV_SERVER_PORT = "m3u_remote_control_server_port"
SETTING_BENCHMARK_PLAYLIST_TITLE = "m3u_benchmark_playlist_title"
SETTING_BENCHMARK_PLAYLIST_URL = "m3u_benchmark_playlist_url"


class RemoteControlSubscribeTest(base_test.BaseTestClass):
    def setup_class(self):
        self.ads = self.register_controller(android_device)
        params = self.user_params
        self.repo_root = find_repo_root()
        self.phone_serial = params.get("phone_serial") or first_matching_device(self.ads, is_tv=False)
        self.tv_serial = params.get("tv_serial") or first_matching_device(self.ads, is_tv=True)
        self.phone_apk = configured_apk(
            self.repo_root,
            params.get("phone_apk"),
            "app/smartphone/build/outputs/apk/debug",
        )
        self.tv_apk = configured_apk(
            self.repo_root,
            params.get("tv_apk"),
            "app/tv/build/outputs/apk/debug",
        )
        self.mock_url = params.get("mock_url", "http://127.0.0.1:8080").rstrip("/")
        self.mock_server_port = int(params.get("mock_server_port", 8080))
        self.direct_tv_host = params.get("direct_tv_host")
        self.direct_tv_port = params.get("direct_tv_port")
        self.playlist_title = params.get("playlist_title", "RemoteControlLive")
        self.timeout_seconds = int(params.get("timeout_seconds", 120))
        asserts.assert_true(self.phone_serial, "Missing phone serial")
        asserts.assert_true(self.tv_serial, "Missing TV serial")

    def test_remote_control_subscribe_to_tv(self):
        self._install_and_reset_apps()
        self._launch_apps()
        tv_code = wait_for_six_digit_code(self.tv_serial, self.timeout_seconds)
        logging.info("TV pairing code: %s", tv_code)
        self._enable_remote_control_on_phone()
        self._pair_phone_to_tv(tv_code)
        self._subscribe_for_tv()
        tap_any_text(self.tv_serial, Labels.TV_LIBRARY, 30, package=TV_PACKAGE)
        wait_for_text(self.tv_serial, self.playlist_title, self.timeout_seconds, package=TV_PACKAGE)

    def teardown_class(self):
        phone_serial = getattr(self, "phone_serial", None)
        tv_serial = getattr(self, "tv_serial", None)
        for serial, key in (
            (phone_serial, SETTING_DIRECT_TV_HOST),
            (phone_serial, SETTING_DIRECT_TV_PORT),
            (phone_serial, SETTING_BENCHMARK_PLAYLIST_TITLE),
            (phone_serial, SETTING_BENCHMARK_PLAYLIST_URL),
            (tv_serial, SETTING_TV_SERVER_PORT),
        ):
            if not serial:
                continue
            shell(serial, "settings", "delete", "global", key, check=False)

    def _install_and_reset_apps(self):
        asserts.assert_true(self.phone_apk.exists(), f"Phone APK does not exist: {self.phone_apk}")
        asserts.assert_true(self.tv_apk.exists(), f"TV APK does not exist: {self.tv_apk}")
        adb(self.phone_serial, "install", "-r", str(self.phone_apk), device_scoped=False)
        adb(self.tv_serial, "install", "-r", str(self.tv_apk), device_scoped=False)
        shell(self.phone_serial, "pm", "clear", PHONE_PACKAGE)
        shell(self.tv_serial, "pm", "clear", TV_PACKAGE)
        shell(self.phone_serial, "pm", "grant", PHONE_PACKAGE, "android.permission.POST_NOTIFICATIONS", check=False)
        self._configure_direct_endpoint()
        shell(self.phone_serial, "input", "keyevent", "KEYCODE_WAKEUP")
        shell(self.tv_serial, "input", "keyevent", "KEYCODE_WAKEUP")
        subprocess.run(
            ["adb", "-s", self.tv_serial, "reverse", "--remove", f"tcp:{self.mock_server_port}"],
            check=False,
            text=True,
            capture_output=True,
        )
        adb(self.tv_serial, "reverse", f"tcp:{self.mock_server_port}", f"tcp:{self.mock_server_port}", device_scoped=False)

    def _configure_direct_endpoint(self):
        if self.direct_tv_host and self.direct_tv_port:
            port = str(self.direct_tv_port)
            logging.info("Using direct TV endpoint override %s:%s", self.direct_tv_host, port)
            shell(self.phone_serial, "settings", "put", "global", SETTING_DIRECT_TV_HOST, self.direct_tv_host)
            shell(self.phone_serial, "settings", "put", "global", SETTING_DIRECT_TV_PORT, port)
            shell(self.tv_serial, "settings", "put", "global", SETTING_TV_SERVER_PORT, port)
            subprocess.run(
                ["adb", "-s", self.tv_serial, "forward", "--remove", f"tcp:{port}"],
                check=False,
                text=True,
                capture_output=True,
            )
            adb(self.tv_serial, "forward", f"tcp:{port}", f"tcp:{port}", device_scoped=False)
        else:
            for serial, key in (
                (self.phone_serial, SETTING_DIRECT_TV_HOST),
                (self.phone_serial, SETTING_DIRECT_TV_PORT),
                (self.tv_serial, SETTING_TV_SERVER_PORT),
            ):
                shell(serial, "settings", "delete", "global", key, check=False)

        shell(self.phone_serial, "settings", "put", "global", SETTING_BENCHMARK_PLAYLIST_TITLE, self.playlist_title)
        shell(
            self.phone_serial,
            "settings",
            "put",
            "global",
            SETTING_BENCHMARK_PLAYLIST_URL,
            f"{self.mock_url}/playlist/live.m3u",
        )

    def _launch_apps(self):
        adb(self.phone_serial, "shell", "am", "start", "-n", PHONE_MAIN_ACTIVITY, device_scoped=False)
        shell(self.tv_serial, "monkey", "-p", TV_PACKAGE, "-c", TV_LAUNCH_CATEGORY, "1")
        wait_for_foreground_package(self.phone_serial, PHONE_PACKAGE, 30)
        wait_for_foreground_package(self.tv_serial, TV_PACKAGE, 30)

    def _enable_remote_control_on_phone(self):
        tap_any_text(self.phone_serial, Labels.SETTINGS, 30, package=PHONE_PACKAGE)
        tap_any_text(self.phone_serial, Labels.OPTIONAL_FEATURES, 30, package=PHONE_PACKAGE)
        tap_any_text(self.phone_serial, Labels.REMOTE_CONTROL, 30, package=PHONE_PACKAGE)
        shell(self.phone_serial, "input", "keyevent", "KEYCODE_BACK")

    def _pair_phone_to_tv(self, tv_code):
        tap_remote_control_fab(self.phone_serial, 30)
        wait_for_any_text(self.phone_serial, Labels.CONNECT_TITLE, 30, package=PHONE_PACKAGE)
        tap_pin_code(self.phone_serial, tv_code)
        tap_button_by_text(self.phone_serial, Labels.CONNECT, 30)
        connected = wait_for_any_text(
            self.phone_serial,
            Labels.DISCONNECT + Labels.CONNECT_TIMEOUT,
            self.timeout_seconds,
            package=PHONE_PACKAGE,
        )
        asserts.assert_true(
            connected in Labels.DISCONNECT,
            "Phone did not connect to TV. Local Android emulators often cannot reach each "
            "other through the NSD-advertised device address; run this Mobly test on two "
            "devices in the same LAN, or add an emulator-only endpoint override."
        )
        shell(self.phone_serial, "input", "tap", "24", "24")

    def _subscribe_for_tv(self):
        playlist_url = f"{self.mock_url}/playlist/live.m3u"
        go_to_settings(self.phone_serial)
        tap_button_by_text(self.phone_serial, Labels.PLAYLIST_MANAGEMENT, 30)
        wait_for_text(self.phone_serial, self.playlist_title, 10, package=PHONE_PACKAGE)
        tap_button_by_text(self.phone_serial, Labels.SUBSCRIBE_FOR_TV, 30)
        wait_for_checked_text(self.phone_serial, Labels.SUBSCRIBE_FOR_TV, 10)
        tap_button_by_text(self.phone_serial, Labels.SUBSCRIBE, 30)


class Labels:
    SETTINGS = ["Settings", "设置"]
    OPTIONAL_FEATURES = ["Optional Features", "optional features", "可选功能"]
    REMOTE_CONTROL = ["Remote Control", "remote control", "遥控器"]
    CONNECT_TITLE = ["enter code from TV", "输入电视上的代码", "从电视输入代码"]
    CONNECT = ["CONNECT", "Connect", "连接"]
    DISCONNECT = ["DISCONNECT", "Disconnect", "断开连接"]
    CONNECT_TIMEOUT = [
        "TV was not found. Make sure both devices are on the same network.",
        "未找到电视，请确认两台设备连接到同一个网络",
    ]
    PLAYLIST_MANAGEMENT = ["Playlist Management", "playlist management", "订阅管理"]
    PLAYLIST_NAME = ["PLAYLIST NAME", "playlist name", "订阅名称"]
    PLAYLIST_LINK = ["PLAYLIST LINK", "playlist link", "订阅链接"]
    SUBSCRIBE_FOR_TV = ["FOR TV", "for tv", "为电视订阅"]
    SUBSCRIBE = ["SUBSCRIBE", "Subscribe", "订阅"]
    TV_LIBRARY = ["Library", "媒体库", "资料库"]


def first_matching_device(devices, is_tv):
    for device in devices:
        serial = device.serial
        features = shell(serial, "pm", "list", "features", check=False).stdout
        product = shell(serial, "getprop", "ro.build.characteristics", check=False).stdout
        detected_tv = "android.software.leanback" in features or "tv" in product
        if detected_tv == is_tv:
            return serial
    return None


def tap_any_text(serial, texts, timeout_seconds, package=None):
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        root = dump_window(serial)
        for text in texts:
            node = find_node(root, text, package=package)
            if node is not None:
                left, top, right, bottom = node_bounds(node)
                shell(serial, "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
                return
        time.sleep(1)
    raise AssertionError(f"Timed out waiting for tappable {texts} on {serial}")


def tap_remote_control_fab(serial, timeout_seconds):
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        root = dump_window(serial)
        node = find_node(
            root,
            Labels.REMOTE_CONTROL,
            package=PHONE_PACKAGE,
            content_desc_only=True,
        )
        if node is not None:
            left, top, right, bottom = node_bounds(node)
            shell(serial, "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
            return
        time.sleep(1)
    raise AssertionError(f"Timed out waiting for remote control FAB on {serial}")


def tap_pin_code(serial, code):
    for digit in code:
        tap_keypad_digit(serial, digit, 10)


def tap_keypad_digit(serial, digit, timeout_seconds):
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        root = dump_window(serial)
        for node in root.iter("node"):
            if node.attrib.get("package") != PHONE_PACKAGE:
                continue
            if node.attrib.get("text", "") != digit:
                continue
            left, top, right, bottom = node_bounds(node)
            if top < 1650:
                continue
            shell(serial, "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
            return
        time.sleep(1)
    raise AssertionError(f"Timed out waiting for keypad digit {digit} on {serial}")


def tap_button_by_text(serial, texts, timeout_seconds):
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        root = dump_window(serial)
        parent_by_child = parent_map(root)
        for text in texts:
            node = find_node(root, text, package=PHONE_PACKAGE)
            if node is None:
                continue
            clickable = nearest_clickable_parent(node, parent_by_child)
            target = clickable if clickable is not None else node
            left, top, right, bottom = node_bounds(target)
            shell(serial, "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
            return
        time.sleep(1)
    raise AssertionError(f"Timed out waiting for button {texts} on {serial}")


def parent_map(root):
    parents = {}
    for parent in root.iter():
        for child in list(parent):
            parents[child] = parent
    return parents


def nearest_clickable_parent(node, parents):
    current = node
    while current is not None:
        if current.attrib.get("clickable") == "true" and current.attrib.get("enabled") == "true":
            return current
        current = parents.get(current)
    return None


def wait_for_any_text(serial, texts, timeout_seconds, package=None):
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        root = dump_window(serial)
        for text in texts:
            if find_node(root, text, package=package) is not None:
                return text
        time.sleep(1)
    raise AssertionError(f"Timed out waiting for {texts} on {serial}")


def wait_for_text(serial, text, timeout_seconds, package=None):
    wait_for_any_text(serial, [text], timeout_seconds, package=package)


def wait_for_text_prefix(serial, text_prefix, timeout_seconds, package=None):
    deadline = time.monotonic() + timeout_seconds
    lowered = text_prefix.lower()
    while time.monotonic() < deadline:
        root = dump_window(serial)
        for node in root.iter("node"):
            if package is not None and node.attrib.get("package") != package:
                continue
            if node.attrib.get("text", "").lower().startswith(lowered):
                return
        time.sleep(1)
    raise AssertionError(f"Timed out waiting for text prefix {text_prefix} on {serial}")


def wait_for_checked_text(serial, texts, timeout_seconds):
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        root = dump_window(serial)
        parent_by_child = parent_map(root)
        for text in texts:
            node = find_node(root, text, package=PHONE_PACKAGE)
            if node is None:
                continue
            current = node
            while current is not None:
                if current.attrib.get("checkable") == "true" and current.attrib.get("checked") == "true":
                    return
                current = parent_by_child.get(current)
        time.sleep(1)
    raise AssertionError(f"Timed out waiting for checked {texts} on {serial}")


def go_to_settings(serial):
    if find_any_text(serial, Labels.SETTINGS, package=PHONE_PACKAGE):
        tap_any_text(serial, Labels.SETTINGS, 5, package=PHONE_PACKAGE)
        return
    shell(serial, "input", "keyevent", "KEYCODE_BACK")
    tap_any_text(serial, Labels.SETTINGS, 30, package=PHONE_PACKAGE)


def find_any_text(serial, texts, package=None):
    root = dump_window(serial)
    return any(find_node(root, text, package=package) is not None for text in texts)


def wait_for_six_digit_code(serial, timeout_seconds):
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        root = dump_window(serial)
        for node in root.iter("node"):
            for attr in ("text", "content-desc"):
                value = node.attrib.get(attr, "")
                if value.isdigit() and len(value) == 6:
                    return value
        time.sleep(1)
    raise AssertionError(f"Timed out waiting for TV pairing code on {serial}")


def type_text(serial, value):
    for index in range(0, len(value), 4):
        shell(serial, "input", "text", adb_text(value[index:index + 4]))
        time.sleep(0.15)


def dump_window(serial):
    shell(serial, "uiautomator", "dump", "/data/local/tmp/window.xml")
    time.sleep(0.25)
    result = shell(serial, "cat", "/data/local/tmp/window.xml")
    return ET.fromstring(result.stdout)


def find_node(root, text, package=None, content_desc_only=False):
    texts = text if isinstance(text, list) else [text]
    lowered_texts = [value.lower() for value in texts]
    for node in root.iter("node"):
        if package is not None and node.attrib.get("package") != package:
            continue
        node_text = node.attrib.get("text", "")
        desc = node.attrib.get("content-desc", "")
        if content_desc_only:
            if desc.lower() in lowered_texts:
                return node
            continue
        if node_text.lower() in lowered_texts or desc.lower() in lowered_texts:
            return node
    return None


def wait_for_foreground_package(serial, package, timeout_seconds):
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if foreground_package(serial) == package:
            return
        time.sleep(1)
    raise AssertionError(
        f"Timed out waiting for foreground package {package} on {serial}; current={foreground_package(serial)}"
    )


def foreground_package(serial):
    result = shell(serial, "dumpsys", "window", check=False)
    match = re.search(r"mCurrentFocus=.*? ([^/\s]+)/", result.stdout)
    if match:
        return match.group(1)
    match = re.search(r"mFocusedApp=.*? ([^/\s]+)/", result.stdout)
    return match.group(1) if match else None


def node_bounds(node):
    bounds = node.attrib["bounds"]
    left_top, right_bottom = bounds.split("][")
    left, top = left_top.strip("[").split(",")
    right, bottom = right_bottom.strip("]").split(",")
    return int(left), int(top), int(right), int(bottom)


def adb_text(value):
    return (
        value.replace("%", "%25")
        .replace(" ", "%s")
        .replace("&", "\\&")
        .replace("?", "\\?")
        .replace("=", "\\=")
        .replace(":", "\\:")
        .replace("/", "\\/")
    )


def shell(serial, *args, check=True):
    return adb(serial, "shell", *args, check=check, device_scoped=False)


def adb(serial, *args, check=True, device_scoped=True):
    command = ["adb"]
    if serial:
        command.extend(["-s", serial])
    if device_scoped:
        command.append("shell")
    command.extend(args)
    result = subprocess.run(command, check=False, text=True, capture_output=True)
    if check and result.returncode != 0:
        raise AssertionError(
            "Command failed: " + " ".join(command) + "\n" + result.stdout + "\n" + result.stderr
        )
    return result


def repo_file(repo_root, path):
    candidate = Path(path)
    if candidate.is_absolute():
        return candidate
    return repo_root / candidate


def configured_apk(repo_root, configured_path, apk_dir):
    if configured_path:
        return repo_file(repo_root, configured_path)
    output_dir = repo_file(repo_root, apk_dir)
    metadata_path = output_dir / "output-metadata.json"
    with metadata_path.open() as metadata_file:
        metadata = json.load(metadata_file)
    output_file = metadata["elements"][0]["outputFile"]
    return output_dir / output_file


def find_repo_root():
    current = Path(os.getcwd()).resolve()
    while True:
        if (current / "settings.gradle.kts").exists():
            return current
        if current.parent == current:
            raise AssertionError(f"Cannot find repository root from {os.getcwd()}")
        current = current.parent


if __name__ == "__main__":
    test_runner.main()
