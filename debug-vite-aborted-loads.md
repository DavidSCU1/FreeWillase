# [OPEN] Debug Session: vite-aborted-loads

## Symptom
- Browser preview shows multiple `net::ERR_ABORTED` requests against `http://localhost:5173/` and Vite-served module URLs.

## Expected
- Vite dev server stays available and route/module requests load normally.

## Hypotheses
1. Vite dev server exits or restarts unexpectedly, aborting all in-flight requests.
2. A recent frontend change introduced a compile-time error that breaks module serving.
3. Dependency prebundle state is inconsistent, causing `node_modules/.vite/deps/*` requests to fail.
4. The `/prediction` page or its imports trigger a runtime/build issue that cascades into aborted requests.
5. The browser preview is pointing at a port with no stable process behind it.

## Evidence Log
- `npm run dev -- --host 127.0.0.1` starts successfully; Vite reports ready on `http://127.0.0.1:5173/`.
- Terminal shows `Re-optimizing dependencies because lockfile has changed`, which can temporarily abort in-flight browser module requests.
- Browser verification confirms frontend assets load, but `/prediction` is redirected to `/login`.
- Original login flow called `http://localhost:8081/api/auth/login` directly instead of using Vite proxy.
- After front-end fix, login request goes to `http://127.0.0.1:5173/api/auth/login` via proxy.
- Current remaining failure is backend auth response `403`, not a Vite asset-loading crash.
- Direct POST to `http://localhost:8081/api/auth/login` with `admin/admin123` returns `200`, proving credentials are valid.
- POST with header `Origin: http://127.0.0.1:5173` returns `403 Invalid CORS request`.
- POST with header `Origin: http://localhost:5173` returns `200`, confirming the backend CORS whitelist is too narrow.

## Next Step
- Restart backend so updated CORS configuration takes effect, then verify login from both `localhost:5173` and `127.0.0.1:5173`.
