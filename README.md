# Pufferfish

YouTube Music, laid out like Spotify and coloured the Material You way.

- **Home**: a greeting, your recent songs as shortcuts, Jump back in, your top artists, Made for you mixes, More like what you just played, and YouTube's music chart.
- **Search**: Browse all genre cards; results lead with the artist or song you meant, then songs, artists, albums and playlists.
- **Your Library**: Liked Songs, your own playlists, followed artists, saved albums and downloads.
- **Offline**: download songs, albums and playlists, and they play with no connection.
- **The player**: the whole app takes its colours from the album art (MaterialKolor), a wavy seek bar, karaoke-style word-timed lyrics, a queue, shuffle, repeat, song radio and a sleep timer.
- Background playback with a media notification, a home-screen widget, and a radio that keeps going after your queue.

Part of [Shoal](https://github.com/ShoalApps). GPL-3.0. Not affiliated with YouTube or Google.

## Thanks
- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0): search and streams
- [Media3](https://github.com/androidx/media) (Apache 2.0): playback
- Lyrics: [BetterLyrics](https://github.com/better-lyrics/better-lyrics) (word-timed), [LRCLIB](https://lrclib.net), NetEase Cloud Music
- [MaterialKolor](https://github.com/jordond/materialkolor) (MIT): colour schemes from album art
- Design ideas from [PixelPlayer](https://github.com/PixelPlayerHQ/PixelPlayer) and [Meld](https://github.com/FrancescoGrazioso/Meld) / [Metrolist](https://github.com/MetrolistGroup/Metrolist) (GPL-3.0)
- [Coil](https://github.com/coil-kt/coil) (Apache 2.0): images

## Privacy and legal

No ads, no tracking, nothing sent to us unless you agree to anonymous error reports. See the [privacy policy](https://shoalapps.github.io/privacy.html) and [legal notices](https://shoalapps.github.io/legal.html).

## Support

Shoal is free, open source and has no ads. If you'd like to help it keep going, you can [support Shoal on Liberapay](https://liberapay.com/ShoalApps).

## Release notes

Update `app/src/main/assets/whats-new.json` with each release. Notes ship with the APK, appear once per installed version and can be reopened from Settings. Keep them specific to changes in that release.

## App language

The apps follow Android's system language by default, with an App language picker in Settings and right-to-left layout support. Translations are bundled and work offline. Coverage varies: many phrases are automatically translated, community catalogues may be partial, and missing strings fall back to British English. See `tools/README.md` and `app/src/main/assets/i18n/coverage.json` for maintenance and actual coverage.
