"""Self-signed TLS identity for local-network connections.

On Tailscale, WireGuard already encrypts and authenticates the PC. On a LAN nothing does, so the
bridge serves HTTPS there with a certificate the phone pins: its SHA-256 travels in the pairing
QR code. A device that merely holds the PC's old IP address cannot present that certificate, so
the app refuses it before sending the device token.
"""
from __future__ import annotations

import base64
import hashlib
import os
import ssl
import subprocess
from pathlib import Path


def ensure_identity(directory: Path) -> tuple[Path, Path]:
    """Create (once) and return (cert.pem, key.pem). Uses the openssl CLI: no extra dependency."""
    directory.mkdir(parents=True, exist_ok=True)
    os.chmod(directory, 0o700)
    cert, key = directory / "tls-cert.pem", directory / "tls-key.pem"
    if cert.exists() and key.exists():
        return cert, key
    old_umask = os.umask(0o077)
    try:
        subprocess.run([
            "openssl", "req", "-x509", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:P-256",
            "-nodes", "-days", "3650", "-subj", "/CN=hermes-remote-bridge",
            "-keyout", str(key), "-out", str(cert),
        ], check=True, capture_output=True, timeout=30)
    finally:
        os.umask(old_umask)
    os.chmod(key, 0o600)
    return cert, key


def cert_pin(cert: Path) -> str:
    """base64url(SHA-256(DER certificate)), unpadded. The value the app pins."""
    der = ssl.PEM_cert_to_DER_cert(cert.read_text())
    return base64.urlsafe_b64encode(hashlib.sha256(der).digest()).decode().rstrip("=")
