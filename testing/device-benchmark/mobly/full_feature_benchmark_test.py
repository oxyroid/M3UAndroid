import json
import logging
import time
from pathlib import Path

from mobly import asserts
from mobly import base_test
from mobly import signals
from mobly import test_runner
from mobly.controllers import android_device

import remote_control_subscribe_test as rc

PHONE_PACKAGE = rc.PHONE_PACKAGE
PHONE_MAIN_ACTIVITY = rc.PHONE_MAIN_ACTIVITY
TV_PACKAGE = rc.TV_PACKAGE
SETTING_DATA_SOURCE = "m3u_benchmark_data_source"
SETTING_PLAYLIST_TITLE = rc.SETTING_BENCHMARK_PLAYLIST_TITLE
SETTING_PLAYLIST_URL = rc.SETTING_BENCHMARK_PLAYLIST_URL
SETTING_EPG_TITLE = "m3u_benchmark_epg_title"
SETTING_EPG_URL = "m3u_benchmark_epg_url"
SETTING_XTREAM_TITLE = "m3u_benchmark_xtream_title"
SETTING_XTREAM_BASIC_URL = "m3u_benchmark_xtream_basic_url"
SETTING_XTREAM_USERNAME = "m3u_benchmark_xtream_username"
SETTING_XTREAM_PASSWORD = "m3u_benchmark_xtream_password"
DEFAULT_RAW_BASE = "https://raw.githubusercontent.com/oxyroid/M3UAndroid/master/testdata"


class FullFeatureBenchmarkTest(base_test.BaseTestClass):
    """End-to-end benchmark coverage for the app's top-level user features."""

    def setup_class(self):
        self.ads = self.register_controller(android_device)
        params = self.user_params
        self.repo_root = rc.find_repo_root()
        self.phone_serial = params.get("phone_serial") or rc.first_matching_device(self.ads, is_tv=False)
        self.tv_serial = params.get("tv_serial") or rc.first_matching_device(self.ads, is_tv=True)
        self.phone_apk = rc.configured_apk(
            self.repo_root,
            params.get("phone_apk"),
            "app/smartphone/build/outputs/apk/debug",
        )
        self.tv_apk = rc.configured_apk(
            self.repo_root,
            params.get("tv_apk"),
            "app/tv/build/outputs/apk/debug",
        )
        self.mock_url = params.get("mock_url", "http://127.0.0.1:8080").rstrip("/")
        self.mock_server_port = int(params.get("mock_server_port", 8080))
        self.direct_tv_host = params.get("direct_tv_host")
        self.direct_tv_port = params.get("direct_tv_port")
        self.raw_testdata_base = params.get("raw_testdata_base", DEFAULT_RAW_BASE).rstrip("/")
        self.timeout_seconds = int(params.get("timeout_seconds", 120))
        self.metrics = []
        self.metrics_path = Path(params.get(
            "metrics_path",
            self.repo_root / "testing/device-benchmark/build/mobly-results/feature-benchmark-metrics.json",
        ))
        asserts.assert_true(self.phone_serial, "Missing phone serial")
        asserts.assert_true(self.phone_apk.exists(), f"Phone APK does not exist: {self.phone_apk}")
        self._install_and_reset_phone()

    def test_010_subscribe_m3u_from_raw_fixture(self):
        title = "BenchRawLive"
        url = f"{self.raw_testdata_base}/playlists/live.m3u"
        self._set_subscription_prefill(source="m3u", title=title, url=url)
        self._benchmark("subscribe.m3u.raw", lambda: self._subscribe_current_prefill(title))

    def test_020_subscribe_epg_from_raw_fixture(self):
        title = "BenchRawEpg"
        url = f"{self.raw_testdata_base}/epg/sample.xml"
        self._set_subscription_prefill(source="epg", epg_title=title, epg_url=url)
        self._benchmark("subscribe.epg.raw", lambda: self._subscribe_from_settings())

    def test_030_subscribe_xtream_from_mock_api(self):
        title = "BenchXtream"
        self._set_subscription_prefill(
            source="xtream",
            xtream_title=title,
            xtream_basic_url=self.mock_url,
            xtream_username="m3u",
            xtream_password="m3u",
        )
        self._benchmark("subscribe.xtream.mock", lambda: self._subscribe_current_prefill(title))

    def test_040_browse_library_playlist_and_player(self):
        self._benchmark("library.open", lambda: self._open_destination(Labels.FOR_YOU))
        self._benchmark("playlist.open", lambda: self._open_playlist_and_wait("BenchXtream", Labels.MOCK_NEWS))
        self._benchmark("player.open", lambda: self._open_channel_player(Labels.MOCK_NEWS))

    def test_050_visit_favorite_extension_and_settings_paths(self):
        self._benchmark("favorite.open", lambda: self._open_destination(Labels.FAVORITE))
        self._benchmark("extension.open", lambda: self._open_destination(Labels.EXTENSION))
        self._benchmark("settings.open", lambda: self._open_destination(Labels.SETTINGS))
        self._benchmark("settings.playlists.open", lambda: rc.tap_button_by_text(
            self.phone_serial,
            rc.Labels.PLAYLIST_MANAGEMENT,
            30,
        ))

    def test_060_remote_control_pair_and_subscribe_to_tv(self):
        if not self.tv_serial:
            raise signals.TestSkip("No TV device configured for remote-control benchmark")
        asserts.assert_true(self.tv_apk.exists(), f"TV APK does not exist: {self.tv_apk}")
        remote = rc.RemoteControlSubscribeTest.__new__(rc.RemoteControlSubscribeTest)
        remote.repo_root = self.repo_root
        remote.phone_serial = self.phone_serial
        remote.tv_serial = self.tv_serial
        remote.phone_apk = self.phone_apk
        remote.tv_apk = self.tv_apk
        remote.mock_url = self.mock_url
        remote.mock_server_port = self.mock_server_port
        remote.direct_tv_host = self.direct_tv_host
        remote.direct_tv_port = self.direct_tv_port
        remote.playlist_title = "BenchRemoteTv"
        remote.timeout_seconds = self.timeout_seconds
        self._benchmark("remote_control.subscribe_tv", remote.test_remote_control_subscribe_to_tv)

    def teardown_class(self):
        self.metrics_path.parent.mkdir(parents=True, exist_ok=True)
        self.metrics_path.write_text(json.dumps(self.metrics, indent=2), encoding="utf-8")
        for key in (
            SETTING_DATA_SOURCE,
            SETTING_PLAYLIST_TITLE,
            SETTING_PLAYLIST_URL,
            SETTING_EPG_TITLE,
            SETTING_EPG_URL,
            SETTING_XTREAM_TITLE,
            SETTING_XTREAM_BASIC_URL,
            SETTING_XTREAM_USERNAME,
            SETTING_XTREAM_PASSWORD,
            rc.SETTING_DIRECT_TV_HOST,
            rc.SETTING_DIRECT_TV_PORT,
            rc.SETTING_TV_SERVER_PORT,
        ):
            serial = self.tv_serial if key == rc.SETTING_TV_SERVER_PORT else self.phone_serial
            if serial:
                rc.shell(serial, "settings", "delete", "global", key, check=False)

    def _benchmark(self, name, block):
        start = time.perf_counter()
        try:
            return block()
        finally:
            duration_ms = int((time.perf_counter() - start) * 1000)
            self.metrics.append({"name": name, "duration_ms": duration_ms})
            logging.info("BENCHMARK feature=%s duration_ms=%s", name, duration_ms)

    def _install_and_reset_phone(self):
        rc.adb(self.phone_serial, "install", "-r", str(self.phone_apk), device_scoped=False)
        rc.shell(self.phone_serial, "pm", "clear", PHONE_PACKAGE)
        rc.shell(self.phone_serial, "pm", "grant", PHONE_PACKAGE, "android.permission.POST_NOTIFICATIONS", check=False)
        rc.shell(self.phone_serial, "input", "keyevent", "KEYCODE_WAKEUP")
        rc.adb(
            self.phone_serial,
            "reverse",
            f"tcp:{self.mock_server_port}",
            f"tcp:{self.mock_server_port}",
            device_scoped=False,
        )
        rc.adb(self.phone_serial, "shell", "am", "start", "-n", PHONE_MAIN_ACTIVITY, device_scoped=False)
        rc.wait_for_foreground_package(self.phone_serial, PHONE_PACKAGE, 30)

    def _set_subscription_prefill(
        self,
        source,
        title=None,
        url=None,
        epg_title=None,
        epg_url=None,
        xtream_title=None,
        xtream_basic_url=None,
        xtream_username=None,
        xtream_password=None,
    ):
        values = {
            SETTING_DATA_SOURCE: source,
            SETTING_PLAYLIST_TITLE: title,
            SETTING_PLAYLIST_URL: url,
            SETTING_EPG_TITLE: epg_title,
            SETTING_EPG_URL: epg_url,
            SETTING_XTREAM_TITLE: xtream_title,
            SETTING_XTREAM_BASIC_URL: xtream_basic_url,
            SETTING_XTREAM_USERNAME: xtream_username,
            SETTING_XTREAM_PASSWORD: xtream_password,
        }
        for key, value in values.items():
            if value:
                rc.shell(self.phone_serial, "settings", "put", "global", key, value)
            else:
                rc.shell(self.phone_serial, "settings", "delete", "global", key, check=False)

    def _subscribe_current_prefill(self, expected_title):
        self._subscribe_from_settings()
        self._open_destination(Labels.FOR_YOU)
        rc.wait_for_text(self.phone_serial, expected_title, self.timeout_seconds, package=PHONE_PACKAGE)

    def _subscribe_from_settings(self):
        self._open_destination(rc.Labels.SETTINGS)
        rc.tap_button_by_text(self.phone_serial, rc.Labels.PLAYLIST_MANAGEMENT, 30)
        time.sleep(1)
        rc.tap_button_by_text(self.phone_serial, rc.Labels.SUBSCRIBE, 30)
        time.sleep(3)

    def _open_destination(self, labels):
        rc.adb(self.phone_serial, "shell", "am", "start", "-n", PHONE_MAIN_ACTIVITY, device_scoped=False)
        rc.wait_for_foreground_package(self.phone_serial, PHONE_PACKAGE, 30)
        try:
            rc.tap_any_text(self.phone_serial, labels, 10, package=PHONE_PACKAGE)
        except AssertionError:
            rc.shell(self.phone_serial, "input", "keyevent", "KEYCODE_BACK", check=False)
            rc.tap_any_text(self.phone_serial, labels, 30, package=PHONE_PACKAGE)

    def _open_playlist_and_wait(self, playlist_title, expected_channel):
        self._open_destination(Labels.FOR_YOU)
        rc.tap_any_text(self.phone_serial, [playlist_title], self.timeout_seconds, package=PHONE_PACKAGE)
        rc.wait_for_text(self.phone_serial, expected_channel, self.timeout_seconds, package=PHONE_PACKAGE)

    def _open_channel_player(self, channel_title):
        rc.tap_any_text(self.phone_serial, [channel_title], 30, package=PHONE_PACKAGE)
        time.sleep(3)
        rc.wait_for_foreground_package(self.phone_serial, PHONE_PACKAGE, 30)
        rc.shell(self.phone_serial, "input", "keyevent", "KEYCODE_BACK", check=False)


class Labels:
    FOR_YOU = ["For You", "for you", "推荐", "首页"]
    FAVORITE = ["Favourite", "Favorite", "favourite", "favorite", "收藏"]
    EXTENSION = ["Extension", "Extensions", "extension", "extensions", "扩展"]
    SETTINGS = rc.Labels.SETTINGS
    MOCK_NEWS = "Mock News"


if __name__ == "__main__":
    test_runner.main()
