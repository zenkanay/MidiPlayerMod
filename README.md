# MidiPlayerMod

A client-side Fabric mod for Minecraft 1.21.11 that plays MIDI files on surrounding note blocks automatically.

## Features

* Plays MIDI files on surrounding note blocks in real-time.
* Automatically tunes nearby note blocks with a smart auto-tuning algorithm.
* Provides a vanilla-styled configuration screen (default key is **M**, configurable in controls settings).

## Requirements

* **Minecraft**: `1.21.11`
* **Mod Loader**: `Fabric`
* **Dependencies**:
  * `Fabric API`

## Instrument Mapping Table

The mod maps MIDI instrument categories to Minecraft note block instruments in the following preferred order:

| MIDI Category | Preferred Note Block Instrument (Preferred Order) | Target Sound in Minecraft |
| :--- | :--- | :--- |
| **PIANO** | `HARP`, `PLING` | Piano/Harp |
| **CHROMATIC** | `XYLOPHONE`, `IRON_XYLOPHONE`, `CHIME`, `BELL`, `HARP` | Melodic Percussion |
| **ORGAN** | `PLING`, `BIT`, `HARP` | Electronic Organ |
| **GUITAR** | `GUITAR`, `BANJO`, `HARP` | Plucked Strings |
| **BASS** | `BASS`, `DIDGERIDOO`, `HARP` | Bass Lines |
| **STRINGS** / **ENSEMBLE**| `HARP`, `PLING` | String Section |
| **BRASS** | `FLUTE`, `DIDGERIDOO`, `HARP` | Horns |
| **REED** / **PIPE** | `FLUTE`, `HARP` | Woodwinds |
| **SYNTH_LEAD** | `BIT`, `PLING`, `HARP` | Synth Lead |
| **SYNTH_PAD** | `PLING`, `BIT`, `HARP` | Synth Pad |
| **SYNTH_SFX** / **SFX** | `BIT`, `HARP` | Synth SFX |
| **ETHNIC** | `BANJO`, `DIDGERIDOO`, `HARP` | World Folk Instruments |
| **PERCUSSIVE** | `COW_BELL`, `XYLOPHONE`, `HARP` | Percussive Hits |
| **DRUMS** | `BASEDRUM`, `SNARE`, `HAT` | Drums (Non-pitched) |

## License

This project is licensed under the [MIT License](LICENSE).
