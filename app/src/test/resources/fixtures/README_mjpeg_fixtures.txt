SYNTHETIC MJPEG FIXTURES — Phase 10 (webcam-streaming), plan 10-01, Wave 0
==========================================================================

These three .bin files are the ONLY synthetic byte-stream fixtures in this
project, and they are synthetic on purpose:

  Neither of this project's test printers (Ender 5 Plus @ 192.168.1.120,
  Ender 3 Pro @ 192.168.1.121) exposes a decodable MJPEG
  `multipart/x-mixed-replace` stream — both run ravens-perch / MediaMTX and
  register every camera as `service: "webrtc-mediamtx"` (the DEFERRED
  WebRTC case; a plain GET of the stream_url 404s with text/plain and is
  never multipart). See 10-RESEARCH.md § Pitfall 2 (verified live 2026-06-03).

Because there is NO live MJPEG subject to capture, these golden byte streams
ARE the authoritative MJPEG-decode proof for CAM-01 (see 10-RESEARCH.md
Open Q1, RESOLVED). They are hand-synthesized 2–3-frame
`multipart/x-mixed-replace` bodies, NOT captured from a real camera.

Shared wire format
------------------
  Boundary token: `dinghyboundary`  (no surrounding quotes)
  Each part:
    --dinghyboundary\r\n
    Content-Type: image/jpeg\r\n
    [Content-Length: NNNN\r\n]   <- present or omitted, see per-file below
    \r\n
    <JPEG bytes: SOI FF D8 ... a tiny FFFE COM segment ... EOI FF D9>
    \r\n
  Trailer: --dinghyboundary--\r\n

  The JPEGs are MINIMAL but structurally valid (SOI + a COM comment segment +
  EOI). Successive frames carry a different COM payload byte (0xA0, 0xA1, ...)
  so a decoder test can assert frame N differs from frame N-1 if desired.

Files
-----
  mjpeg_with_content_length.bin  (3 frames)
      Content-Length present on EVERY part. The decoder reads exactly that
      many bytes per part (the common ustreamer case).

  mjpeg_no_content_length.bin    (3 frames)
      Content-Length OMITTED on every part. Forces the decoder's
      boundary / SOI(FF D8)–EOI(FF D9) scan path (the some-servers case).
      Guarantee: the literal string "Content-Length" does NOT appear in this
      file.

  mjpeg_split_jpeg.bin           (2 frames, Content-Length present)
      Same wire format as mjpeg_with_content_length.bin. The SPLIT is NOT a
      property of these bytes — it is a READ-CHUNKING behavior that
      FakeMjpegStream applies when replaying this fixture: it delivers the
      bytes in arbitrary-sized chunks INCLUDING a chunk boundary that falls
      in the middle of a JPEG payload, so the decoder is proven to reassemble
      a JPEG split across two source.read() calls. The decoder/test reads the
      boundary token from FakeMjpegStream.BOUNDARY (= "dinghyboundary").

Do NOT replace these with a captured stream unless a real crowsnest/ustreamer
MJPEG camera is stood up — and even then, KEEP these as the deterministic
unit-test corpus (live captures are flaky; goldens are not).
