import os
import sys
import time
import unittest

from tools import vercel_cli

PYTHON = sys.executable


class StreamTests(unittest.TestCase):
    def test_output_is_streamed_and_returned(self):
        code, out = vercel_cli.run(
            [PYTHON, "-c", "print('one'); print('two')"], os.environ, timeout=30)
        self.assertEqual(code, 0)
        self.assertEqual(out, "one\ntwo\n")

    def test_a_failure_status_is_returned_with_its_output(self):
        code, out = vercel_cli.run(
            [PYTHON, "-c", "import sys; print('boom'); sys.exit(3)"], os.environ, timeout=30)
        self.assertEqual(code, 3)
        self.assertIn("boom", out)

    def test_a_stall_is_killed_at_the_deadline(self):
        began = time.monotonic()
        with self.assertRaises(vercel_cli.Timeout) as stalled:
            vercel_cli.run([PYTHON, "-c", "import time; time.sleep(60)"],
                           os.environ, timeout=1)
        self.assertLess(time.monotonic() - began, 15)
        self.assertEqual(stalled.exception.timeout, 1)


class CaptureTests(unittest.TestCase):
    def test_streams_stay_apart(self):
        code, out, err = vercel_cli.capture(
            [PYTHON, "-c", "import sys; print('out'); print('err', file=sys.stderr)"],
            os.environ, timeout=30)
        self.assertEqual(code, 0)
        self.assertEqual(out, "out\n")
        self.assertEqual(err, "err\n")

    def test_a_failure_keeps_the_error_stream(self):
        code, out, err = vercel_cli.capture(
            [PYTHON, "-c", "import sys; print('detail', file=sys.stderr); sys.exit(2)"],
            os.environ, timeout=30)
        self.assertEqual(code, 2)
        self.assertEqual(err, "detail\n")

    def test_a_stall_is_killed_at_the_deadline(self):
        began = time.monotonic()
        with self.assertRaises(vercel_cli.Timeout):
            vercel_cli.capture([PYTHON, "-c", "import time; time.sleep(60)"],
                               os.environ, timeout=1)
        self.assertLess(time.monotonic() - began, 15)


if __name__ == "__main__":
    unittest.main()
