# MidiPlayerMod

A client-side Fabric mod for Minecraft 1.21.11 that plays MIDI files on surrounding note blocks automatically.

## Usage

1. Place note blocks around you (within 4.5m radius).
2. Press the Config GUI key (default is **M**, configurable in Minecraft's controls settings) to open the configuration screen.
3. Select a MIDI file, click **Auto-Tune**, and click **Play**.

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
