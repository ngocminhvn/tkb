#!/usr/bin/env python3
"""Regression guards for TNUT portal authentication renewal (no network)."""
from pathlib import Path

root = Path("app/src/main/java/com/sv/tnu")
client = (root / "schools/tnut/TnutApiClient.kt").read_text(encoding="utf-8")
store = (root / "data/AuthStore.kt").read_text(encoding="utf-8")
screen = (root / "ui/TimetableScreen.kt").read_text(encoding="utf-8")

# Application interceptors see HTTP 200 / logical code 401 only after
# OkHttp Authenticator. They must attempt the refresh + explicit retry.
assert 'private fun isLogicalUnauthorized(' in client
assert 'response.peekBody(4096)' in client
assert 'portalUnauthorized.containsMatchIn(body)' in client
assert 'body.contains("notallowed", ignoreCase = true)' in client
assert 'val newToken = renewSession(authStore, oldToken)' in client
assert 'response.close()' in client
assert client.index('response.close()') < client.index('chain.proceed(retry)')
assert 'response.newBuilder()' in client
assert '.code(401)' in client

# Actual HTTP 401 is handled by Authenticator, using the same renewal lock.
assert 'private val refreshLock = Any()' in client
assert 'synchronized(refreshLock)' in client
assert 'if (current != null && current != rejectedToken)' in client
assert 'val newToken = renewSession(authStore, rejectedToken)' in client
assert 'if (responseCount(response) >= 2) return null' in client
assert 'response.request.url.encodedPath.endsWith("auth/login")' in client
assert '.addInterceptor(authInterceptor)' in client
assert '.header("Authorization", "Bearer $newToken")' in client
assert '.addHeader("Authorization"' not in client

# The plain client does not add auth interceptors; a bad refresh token can
# recover using credentials already saved for the portal login.
assert 'runBlocking { plainApi.refreshToken(' in client
assert 'runBlocking { plainApi.login(username = username, password = password) }' in client
assert 'val validSession = session?.takeIf { it.accessToken.isNotBlank() }' in client

# A missing rotated refresh_token must never delete the existing credential.
tokens_block = store.split('fun saveTokens(response: SchoolSessionTokens)', 1)[1].split('fun clear()', 1)[0]
assert 'response.refreshToken?.takeIf { it.isNotBlank() }?.let {' in tokens_block
assert 'editor.putString(KEY_REFRESH_TOKEN, it)' in tokens_block
assert '.putString(KEY_REFRESH_TOKEN, response.refreshToken)' not in tokens_block

# A rejected session must keep Room cache/notes and clearly guide re-login.
assert 'cause is retrofit2.HttpException && cause.code() == 401' in screen
assert 'Lịch đã lưu vẫn được giữ.' in screen
print("OK: TNUT session refresh/re-login guards and non-destructive UI")
