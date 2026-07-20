---
name: Playback issue
about: Report stream playback, decoder, buffering, or Media3/ExoPlayer behavior
title: '[Playback] '
labels: bug, playback
assignees: ''

---

**Check before feedback**
- [ ] I checked existing issues to ensure this has not already been reported.
- [ ] I reproduced the issue on the latest GitHub Release or Nightly build when possible.
- [ ] I removed or masked private stream URLs, tokens, usernames, passwords, and server addresses.

**App and device**
- App variant:
  - [ ] Smartphone
  - [ ] TV
- Installation source:
  - [ ] GitHub Release
  - [ ] Nightly
  - [ ] Self-built
- Version or commit: [e.g. 1.10.2 or commit SHA]
- Device model: [e.g. Google Pixel 8, Chromecast with Google TV]
- Android version: [e.g. Android 14]
- Is this an Android TV device?
  - [ ] Yes
  - [ ] No
  - [ ] Not sure

**Stream details**
- Stream URL type:
  - [ ] HLS / .m3u8
  - [ ] MPEG-TS / .ts
  - [ ] DASH / .mpd
  - [ ] MP4
  - [ ] RTSP
  - [ ] Other:
- Video codec: [e.g. H.264/AVC, H.265/HEVC, AV1, MPEG-2, unknown]
- Audio codec: [e.g. AAC, AC3, EAC3, MP2, unknown]
- Resolution / frame rate if known: [e.g. 1920x1080 50fps]
- DRM or special headers required?
  - [ ] Yes
  - [ ] No
  - [ ] Not sure

**External player comparison**
- Does the same stream play in an external player?
  - [ ] Yes, VLC
  - [ ] Yes, another player:
  - [ ] No
  - [ ] Not tested
- If it plays externally, note any differences in startup time, audio/video sync, subtitles, or buffering.

**Network environment**
- Connection type:
  - [ ] Wi-Fi
  - [ ] Ethernet
  - [ ] Mobile data
  - [ ] VPN / proxy
- Network notes: [ISP, region, VPN, LAN/NAS, provider rate limits, etc.]

**Media3 / ExoPlayer behavior**
- What happens in M3UAndroid?
  - [ ] Never starts
  - [ ] Buffers repeatedly
  - [ ] Audio only
  - [ ] Video only
  - [ ] Audio/video out of sync
  - [ ] Decoder error
  - [ ] App crash
  - [ ] Other:
- Error message or player error if shown:

**Reproduction steps**
1. Open '...'
2. Play channel or stream '...'
3. Observe '...'

**Sanitized sample and logs**
Attach a sanitized sample playlist entry, stream metadata, Logcat output, screenshot, or screen recording if possible.
