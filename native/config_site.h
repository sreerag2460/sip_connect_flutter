/*
 * PJSIP build configuration for sip_connect_flutter (mobile).
 * Copied into pjproject/pjlib/include/pj/config_site.h by the build scripts.
 *
 * Mobile-tuned: single-threaded-friendly, video enabled, bundled codecs only
 * (Opus is added at P3). Keep this file the single source of PJSIP build knobs.
 */

/* Platform flag is set by the build script before including this file:
 *   Android -> PJ_CONFIG_ANDROID, iOS -> PJ_CONFIG_IPHONE.               */
#if defined(PJ_CONFIG_ANDROID) && PJ_CONFIG_ANDROID!=0
#  include <pj/config_site_sample.h>
#endif
#if defined(PJ_CONFIG_IPHONE) && PJ_CONFIG_IPHONE!=0
#  include <pj/config_site_sample.h>
#endif

/* --- Media / codecs ------------------------------------------------------ */
#define PJMEDIA_HAS_VIDEO                    1
#define PJMEDIA_HAS_G711_CODEC               1   /* PCMU / PCMA            */
#define PJMEDIA_HAS_G722_CODEC               1
#define PJMEDIA_HAS_ILBC_CODEC               1
#define PJMEDIA_HAS_GSM_CODEC                1
#define PJMEDIA_HAS_SPEEX_CODEC              1
/* Opus/G.729 intentionally OFF for the P0 build (added at P3). */
#define PJMEDIA_HAS_OPUS_CODEC               0

/* --- Video codecs (platform hardware H.264; VPX needs libvpx — P4) ------- */
#define PJMEDIA_HAS_VPX_CODEC                0   /* needs cross-built libvpx (P4) */
#if defined(PJ_CONFIG_IPHONE) && PJ_CONFIG_IPHONE!=0
#  define PJMEDIA_HAS_VID_TOOLBOX_CODEC      1   /* iOS HW H.264 */
#endif
/* PJSIP's MediaCodec wrapper needs NDK APIs from Android 28 but we target
 * API 21. P4 either raises minSdk to 28 or brings libvpx for SW video.     */
#define PJMEDIA_HAS_ANDROID_MEDIACODEC       0

/* --- SRTP / TLS ----------------------------------------------------------
 * OpenSSL is cross-compiled per ABI/slice by the build scripts (see
 * VERSIONS.md), enabling TLS transport, SSL sockets and DTLS-SRTP keying.
 */
#define PJMEDIA_HAS_SRTP                     1
#define PJSIP_HAS_TLS_TRANSPORT              1
#define PJ_HAS_SSL_SOCK                      1

/* --- ICE / STUN / TURN --------------------------------------------------- */
#define PJNATH_HAS_STUN                      1

/* --- Sizing (mobile) ----------------------------------------------------- */
#define PJSUA_MAX_CALLS                      8
#define PJSUA_MAX_ACC                        8
#define PJSUA_MAX_PLAYERS                    8
#define PJSUA_MAX_RECORDERS                  8

/* Let the OS pick RTP ports unless InitData.rtpStartPort overrides at runtime */
#define PJSUA_DEFAULT_RTP_PORT               0
