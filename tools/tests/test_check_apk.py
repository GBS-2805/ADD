import unittest

from tools import check_apk

GOOD = """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
  package="app.offline.compress" android:versionCode="41" android:versionName="3.9">
  <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35" />
  <uses-permission android:name="android.permission.INTERNET" />
  <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
  <application android:allowBackup="false" android:usesCleartextTraffic="false">
    <activity android:name="add.MainActivity" android:exported="true">
      <intent-filter><action android:name="android.intent.action.MAIN" /></intent-filter>
    </activity>
    <activity android:name="add.ShelfActivity" android:exported="false" />
  </application>
</manifest>"""

APP = {"id": "add", "version": "3.9", "versionName": "3.9",
       "version_code": 41, "versionCode": 41}


class CheckApkTest(unittest.TestCase):
    def info(self, xml=GOOD):
        return check_apk.inspect_xml(xml)

    def test_accepts_the_current_package_and_version(self):
        self.assertEqual(check_apk.validate(self.info(), APP), [])

    def test_rejects_extra_permissions(self):
        xml = GOOD.replace("<uses-permission android:name=\"android.permission.INTERNET\" />",
                           "<uses-permission android:name=\"android.permission.CAMERA\" />")
        errors = check_apk.validate(self.info(xml), APP)
        self.assertTrue(any("permissions" in error for error in errors))

    def test_rejects_an_exported_activity_without_an_intent_filter(self):
        xml = GOOD.replace('android:name="add.ShelfActivity" android:exported="false"',
                           'android:name="add.ShelfActivity" android:exported="true"')
        errors = check_apk.validate(self.info(xml), APP)
        self.assertTrue(any("no intent filter" in error for error in errors))

    def test_rejects_allow_backup_true(self):
        errors = check_apk.validate(self.info(GOOD.replace('allowBackup="false"',
                                                           'allowBackup="true"')), APP)
        self.assertTrue(any("allowBackup" in error for error in errors))

    def test_rejects_cleartext_traffic(self):
        errors = check_apk.validate(self.info(GOOD.replace('usesCleartextTraffic="false"',
                                                           'usesCleartextTraffic="true"')), APP)
        self.assertTrue(any("usesCleartextTraffic" in error for error in errors))

    def test_rejects_a_published_hash_mismatch(self):
        info = self.info()
        info["sha256"] = "a" * 64
        info["bytes"] = 12
        errors = check_apk.validate(info, APP, {"sha256": "b" * 64, "bytes": 12})
        self.assertTrue(any("published sha256" in error for error in errors))

    def test_rejects_a_wrong_sdk_range(self):
        errors = check_apk.validate(self.info(GOOD.replace('targetSdkVersion="35"',
                                                           'targetSdkVersion="34"')), APP)
        self.assertTrue(any("targetSdkVersion" in error for error in errors))

    def test_rejects_debuggable_release(self):
        errors = check_apk.validate(self.info(GOOD.replace(
            'allowBackup="false"', 'allowBackup="false" android:debuggable="true"')), APP)
        self.assertTrue(any("debuggable" in error for error in errors), errors)

    def test_rejects_a_non_allowlisted_signing_certificate(self):
        signing = {"certificates": [{"sha256": "a" * 64}]}
        errors = check_apk.validate(self.info(), APP, cert="b" * 64, signing=signing)
        self.assertTrue(any("not in the public allowlist" in error for error in errors), errors)

    def test_accepts_the_current_manifest_and_certificate(self):
        signing = {"certificates": [{"sha256": "a" * 64}]}
        self.assertEqual(check_apk.validate(self.info(), APP, cert="a" * 64,
                                            signing=signing), [])

    def test_rejects_a_version_code_below_the_catalog(self):
        errors = check_apk.validate(self.info(GOOD.replace('android:versionCode="41"',
                                                           'android:versionCode="40"')), APP)
        self.assertTrue(any("below the catalog" in error for error in errors), errors)

    def test_release_requires_published_digest(self):
        errors = check_apk.validate(self.info(), APP, release=True)
        self.assertTrue(any("--published" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
