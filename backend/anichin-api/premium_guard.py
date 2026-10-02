"""
premium_guard.py -- gembok premium donghua di SISI SERVER (anichin-api).

Endpoint /episode/<slug> dan /video-source/<slug> cuma dijawab kalau request
bawa token premium bertanda tangan (dibuat edge function zenime-premium-token).
APK mod yang maksa isPremium = true tetap gak punya token valid -> ditolak di sini.

Pasang (di file Flask utama, setelah `app = Flask(...)`):

    import premium_guard
    premium_guard.install(app)

Env (systemd anichin.service, mis. lewat Environment= atau EnvironmentFile=):
    PREMIUM_TOKEN_SECRET  sama persis dengan di edge function (>= 32 char)
    PREMIUM_ENFORCE       "1" buat nyalain. Default mati, jadi aman dideploy
                          duluan sebelum app versi baru tersebar.

Tanpa dependency tambahan (cuma stdlib + Flask).
"""
import base64
import hashlib
import hmac
import json
import os
import time

from flask import jsonify, request

GUARDED_PREFIXES = ("/episode/", "/video-source/")


def _b64url_decode(s: str) -> bytes:
    return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))


def verify_token(token: str, secret: str):
    """Balikin payload kalau token sah & belum kadaluarsa & premium, selain itu None."""
    try:
        head_b64, body_b64, sig_b64 = token.split(".")
        expected = hmac.new(secret.encode(), f"{head_b64}.{body_b64}".encode(), hashlib.sha256).digest()
        if not hmac.compare_digest(expected, _b64url_decode(sig_b64)):
            return None
        if json.loads(_b64url_decode(head_b64)).get("alg") != "HS256":
            return None
        payload = json.loads(_b64url_decode(body_b64))
        if int(payload.get("exp", 0)) < time.time() or not payload.get("prem"):
            return None
        return payload
    except Exception:
        return None


def install(app):
    secret = os.environ.get("PREMIUM_TOKEN_SECRET", "")
    enforce = os.environ.get("PREMIUM_ENFORCE", "0") == "1"

    @app.before_request
    def _premium_guard():
        if not enforce or request.method == "OPTIONS":
            return None
        if not request.path.startswith(GUARDED_PREFIXES):
            return None
        if len(secret) < 32:
            return jsonify(error="server belum dikonfigurasi"), 503  # gagal tertutup

        auth = request.headers.get("Authorization", "")
        token = auth[7:].strip() if auth.lower().startswith("bearer ") else ""
        if not token:
            return jsonify(error="premium_required"), 403
        if verify_token(token, secret) is None:
            return jsonify(error="invalid_or_expired_token"), 401  # app auto-refresh lalu coba lagi
        return None
