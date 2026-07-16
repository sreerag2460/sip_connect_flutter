#!/usr/bin/env bash
#
# Applies source patches to a fetched pjproject tree. Idempotent — safe to run
# on every build. Sourced/called by build-android.sh and build-ios.sh with the
# pjproject source dir as $1.
#
set -euo pipefail
SRC="${1:?usage: apply-patches.sh <pjproject-src-dir>}"

# ---------------------------------------------------------------------------
# Patch 1: guard NULL call->inv deref in pjsua_call.c incoming media-error path
#
# Upstream bug (pjproject 2.15.1): when an INCOMING call's media transport init
# fails synchronously — e.g. the SRTP transport rejecting the offer's media
# transport (PJMEDIA_SRTP_ESDPINTRANSPORT) — the error handler
# on_incoming_call_med_tp_complete2() runs while call->inv is still NULL (the
# invite session is created later in pjsua_call_on_incoming). It then evaluates
# `call->inv->state`, dereferencing NULL -> SIGSEGV at pjsua_call_on_incoming.
# The code's own comment acknowledges the invite state is NULL in this path.
# Add a NULL check; harmless when call->inv is set.
# ---------------------------------------------------------------------------
CALLC="$SRC/pjsip/src/pjsua-lib/pjsua_call.c"

# 1a. The actual incoming-call crash: pjsua_call_on_incoming's synchronous
#     media-init-error branches do `if (call->inv->dlg)` while call->inv is
#     still NULL (the invite session is created later in the same function).
#     The sibling branch a few lines up already guards `call->inv &&
#     call->inv->dlg` — upstream just missed these two.
if grep -q "if (call->inv->dlg) {" "$CALLC"; then
  perl -0pi -e 's/if \(call->inv->dlg\) \{/if (call->inv \&\& call->inv->dlg) {/g' "$CALLC"
  echo "==> Patched: NULL call->inv->dlg guard in pjsua_call.c"
else
  echo "==> pjsua_call.c call->inv->dlg guard already applied"
fi

# 1b. Same class of bug in the media-tp-complete error handler (defensive).
if grep -q "if (call->inv->state > PJSIP_INV_STATE_NULL) {" "$CALLC"; then
  perl -0pi -e 's/if \(call->inv->state > PJSIP_INV_STATE_NULL\) \{/if (call->inv \&\& call->inv->state > PJSIP_INV_STATE_NULL) {/g' "$CALLC"
  echo "==> Patched: NULL call->inv->state guard in pjsua_call.c"
else
  echo "==> pjsua_call.c call->inv->state guard already applied"
fi
