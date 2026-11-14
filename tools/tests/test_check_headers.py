from __future__ import annotations

import unittest

from tools import check_headers


def good_headers() -> dict[str, str]:
    return {
        "content-security-policy": (
            "default-src 'self'; object-src 'none'; frame-ancestors 'none'; "
            "script-src 'self' 'sha256-abc'; style-src 'self' 'unsafe-inline'"),
        "strict-transport-security": "max-age=31536000; includeSubDomains",
        "x-content-type-options": "nosniff",
        "x-frame-options": "DENY",
        "referrer-policy": "strict-origin-when-cross-origin",
        "permissions-policy": "camera=(), microphone=()",
        "cross-origin-opener-policy": "same-origin",
        "cross-origin-resource-policy": "same-origin",
        "x-permitted-cross-domain-policies": "none",
    }


class HeaderTests(unittest.TestCase):
    def check(self, headers: dict[str, str], url: str = "https://example.test/"):
        return check_headers.problems(
            "https://example.test", fetch_fn=lambda _: check_headers.Fetched(url, 200, headers))

    def test_correct_set_passes(self):
        self.assertEqual(self.check(good_headers()), [])

    def test_missing_csp_fails(self):
        headers = good_headers()
        headers.pop("content-security-policy")
        self.assertTrue(any("missing enforcing CSP" in item for item in self.check(headers)))

    def test_report_only_is_not_enforcement(self):
        headers = good_headers()
        headers["content-security-policy-report-only"] = headers.pop("content-security-policy")
        self.assertTrue(any("report-only" in item for item in self.check(headers)))

    def test_unsafe_eval_fails(self):
        headers = good_headers()
        headers["content-security-policy"] += " 'unsafe-eval'"
        self.assertTrue(any("unsafe-eval" in item for item in self.check(headers)))

    def test_short_hsts_fails(self):
        headers = good_headers()
        headers["strict-transport-security"] = "max-age=3600; includeSubDomains"
        self.assertTrue(any("below one year" in item for item in self.check(headers)))

    def test_missing_frame_denial_fails(self):
        headers = good_headers()
        headers["x-frame-options"] = "SAMEORIGIN"
        self.assertTrue(any("x-frame-options" in item for item in self.check(headers)))

    def test_http_redirect_fails(self):
        self.assertTrue(any("insecure" in item for item in self.check(
            good_headers(), url="http://example.test/")))

    def test_wrong_hashed_asset_cache_fails(self):
        headers = good_headers()
        headers["cache-control"] = "public, max-age=60"
        found = check_headers.problems(
            "https://example.test",
            fetch_fn=lambda _: check_headers.Fetched("https://example.test/", 200, headers),
            asset_path="/assets/site.abcdef.css")
        self.assertTrue(any("cache-control" in item for item in found), found)


if __name__ == "__main__":
    unittest.main()
