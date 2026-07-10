# MidiPlayerMod

A client-side Fabric mod for Minecraft 1.21.11 that automatically plays MIDI files on surrounding note blocks in real-time. It features advanced auto-tuning mapping, physical occlusion checks, and an intuitive vanilla-styled configuration screen.

---

## Features

- **Smart Auto-Tuning**: Automatically tunes surrounding note blocks to match the MIDI file's required pitches.
  - **Two-Phase Matching**: Locks existing correct-pitch blocks to minimize right-clicks and prevent pitch stealing.
  - **Duplicate Pad Fill**: Pads out extra blocks beyond the 25-note octave with duplicate pitches based on MIDI note frequencies.
  - **Octave Mapping**: Translates MIDI notes (0-127) to Minecraft note blocks (0-24) using intelligent octave shifting.
- **Physics-Based Interaction Filtering**: Calculates the exact shortest 3D distance to the note block's AABB (bounding box) rather than just the center. 
  - Prevents scan misses for blocks whose center is slightly beyond 4.5 meters but whose edge/corner is within the survival interactive reach.
  - Performs raycasting block occlusion checks and living entity intersection checks to filter out note blocks blocked by solid walls or mobs.
- **Vanilla-Styled GUI**: A polished, two-column options menu matching Minecraft's native options style.
  - Includes a seek bar, play/pause controls, instrument selection filters, and automatic config saving.
  - Press `M` to toggle the config screen open and closed instantly.

---

## Instrument Mapping Table

The mod maps standard MIDI instrument categories to Minecraft's note block instruments (based on the block directly underneath the note block). Below is the preferred order of Note Block Instruments used for each MIDI instrument category:

| MIDI Category | Preferred Note Block Instrument (Preferred Order) | Target Instrument Sound in Minecraft |
| :--- | :--- | :--- |
| **PIANO** | `HARP` (Harp/Piano), `PLING` (Pling) | Soft acoustic piano/harp |
| **CHROMATIC** | `XYLOPHONE` (Xylophone), `IRON_XYLOPHONE` (Vibraphone), `CHIME` (Chime), `BELL` (Bell), `HARP` | Melodic metallic & wooden percussion |
| **ORGAN** | `PLING`, `BIT` (Synth/Bit), `HARP` | Synthesized electric organ |
| **GUITAR** | `GUITAR` (Acoustic Guitar), `BANJO` (Banjo), `HARP` | Plucked acoustic strings |
| **BASS** | `BASS` (Double Bass), `DIDGERIDOO` (Didgeridoo), `HARP` | Deep, low-frequency bass lines |
| **STRINGS** / **ENSEMBLE**| `HARP`, `PLING` | Orchestral string section |
| **BRASS** | `FLUTE` (Flute), `DIDGERIDOO`, `HARP` | High brass woodwinds |
| **REED** / **PIPE** | `FLUTE`, `HARP` | Woodwinds (Flutes, Clarinets, etc.) |
| **SYNTH_LEAD** | `BIT`, `PLING`, `HARP` | Chiptune electronic lead synth |
| **SYNTH_PAD** | `PLING`, `BIT`, `HARP` | Warm background pad synthesizer |
| **SYNTH_SFX** / **SFX** | `BIT`, `HARP` | Electronic sound effects |
| **ETHNIC** | `BANJO`, `DIDGERIDOO`, `HARP` | Traditional world folk instruments |
| **PERCUSSIVE** | `COW_BELL` (Cow Bell), `XYLOPHONE`, `HARP` | High pitched melodic percussive hits |
| **DRUMS** (Non-pitched) | `BASEDRUM` (Bass Drum), `SNARE` (Snare Drum), `HAT` (Hi-Hat) | Non-pitched percussion drum kit beats |

---

## Installation & Usage

### Installation
1. Place the compiled `MidiPlayerMod-1.0.0.jar` into your Minecraft client's `mods` folder.
2. Ensure you have **Fabric API** installed for Minecraft 1.21.11.

### How to Play
1. Place note blocks around you. For a full octave range, place at least 25 blocks of your preferred instrument (e.g., Harps, Basses) within a 4.5-meter radius.
2. In-game, press **M** to open the Configuration Screen.
3. Select a MIDI file from the list.
4. (Optional) Filter which MIDI tracks/instruments to play.
5. Click **Auto-Tune** to automatically calibrate the surrounding blocks.
6. Click **Play** and enjoy the music!

---

## Compilation

To compile and package the mod, execute the following Gradle command in the project root:

```bash
./gradlew clean build
```

The compiled mod will be exported to `build/libs/MidiPlayerMod-1.0.0.jar`.

## License
Licensed under the MIT License. See [LICENSE](LICENSE) for details.
