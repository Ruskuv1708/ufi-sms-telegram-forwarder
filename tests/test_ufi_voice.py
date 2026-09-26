import hashlib
import hmac
import socketserver
import threading
import unittest
from unittest import mock

import ufi_voice


TOKEN = "a" * 64


def read_line(stream) -> str:
    return stream.readline(2 * 1024 * 1024).decode("ascii").strip()


class HandshakeHandler(socketserver.StreamRequestHandler):
    def handle(self) -> None:
        nonce = "b" * 64
        port = self.server.server_address[1]
        self.wfile.write(f"HELLO 2 {nonce}\n".encode("ascii"))
        self.wfile.flush()
        auth = read_line(self.rfile)
        command = read_line(self.rfile)
        expected = hmac.digest(
            TOKEN.encode("ascii"),
            f"{ufi_voice.AUTH_DOMAIN}\n{port}\n{nonce}\n{command}".encode("ascii"),
            hashlib.sha256,
        ).hex()
        if auth != f"AUTH {expected}":
            self.wfile.write(b"ERR AUTH\n")
            return
        if command == "LARGE":
            self.wfile.write(("OK " + "x" * 20000 + "\n").encode("ascii"))
        else:
            self.wfile.write(b"OK PONG\n")
        self.wfile.flush()


class IncompatibleHandler(socketserver.StreamRequestHandler):
    def handle(self) -> None:
        self.wfile.write(b"OLD GATEWAY\n")
        self.wfile.flush()


class MutatedCommandHandler(socketserver.StreamRequestHandler):
    def handle(self) -> None:
        nonce = "c" * 64
        port = self.server.server_address[1]
        self.wfile.write(f"HELLO 2 {nonce}\n".encode("ascii"))
        self.wfile.flush()
        auth = read_line(self.rfile)
        command = read_line(self.rfile)
        self.server.received = (auth, command)
        # Model a command modified in transit after the client made its proof.
        expected = hmac.digest(
            TOKEN.encode("ascii"),
            f"{ufi_voice.AUTH_DOMAIN}\n{port}\n{nonce}\nHANGUP".encode("ascii"),
            hashlib.sha256,
        ).hex()
        if auth != f"AUTH {expected}":
            self.wfile.write(b"ERR AUTH\n")
        else:
            self.wfile.write(b"OK MUTATED\n")
        self.wfile.flush()


class VoiceProtocolTests(unittest.TestCase):
    def serve(self, handler):
        server = socketserver.ThreadingTCPServer(("127.0.0.1", 0), handler)
        server.daemon_threads = True
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        return server

    def config(self) -> dict[str, str]:
        return {"host": "127.0.0.1", "token": TOKEN}

    def test_challenge_response_and_large_control_payload(self) -> None:
        server = self.serve(HandshakeHandler)
        with (
            mock.patch.object(ufi_voice, "CONTROL_PORT", server.server_address[1]),
            mock.patch.object(ufi_voice, "validate_config"),
        ):
            response = ufi_voice.control_request(self.config(), "LARGE")
        self.assertEqual(len(response), 20000)
        self.assertEqual(set(response), {"x"})

    def test_incompatible_plaintext_gateway_is_rejected(self) -> None:
        server = self.serve(IncompatibleHandler)
        with (
            mock.patch.object(ufi_voice, "CONTROL_PORT", server.server_address[1]),
            mock.patch.object(ufi_voice, "validate_config"),
        ):
            with self.assertRaisesRegex(ufi_voice.VoiceError, "Incompatible gateway"):
                ufi_voice.control_request(self.config(), "PING")

    def test_command_changed_after_proof_is_rejected(self) -> None:
        server = self.serve(MutatedCommandHandler)
        with (
            mock.patch.object(ufi_voice, "CONTROL_PORT", server.server_address[1]),
            mock.patch.object(ufi_voice, "validate_config"),
        ):
            with self.assertRaisesRegex(ufi_voice.VoiceError, "AUTH"):
                ufi_voice.control_request(self.config(), "PING")
        self.assertEqual(server.received[1], "PING")

    def test_config_rejects_dns_and_non_modem_addresses(self) -> None:
        for host in ("example.com", "127.0.0.1", "192.168.100.0", "192.168.100.255"):
            with self.subTest(host=host):
                with self.assertRaises(ufi_voice.VoiceError):
                    ufi_voice.validate_config({"host": host, "token": TOKEN})
        ufi_voice.validate_config({"host": "192.168.100.1", "token": TOKEN})


if __name__ == "__main__":
    unittest.main()
