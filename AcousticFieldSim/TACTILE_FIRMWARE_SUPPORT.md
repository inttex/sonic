# Tactile Firmware Support

## Overview
Added support for the FPGA Tactile Modulation firmware to the AcousticFieldSim Java application.

## Changes Made

### 1. New Protocol Class: `SimpleFPGA_Tactile.java`
**Location:** `src/acousticfield3d/protocols/SimpleFPGA_Tactile.java`

**Key Features:**
- Uses **16 phase divisions** instead of 32 (matches tactile firmware's reduced resolution)
- Supports amplitude modulation step commands (`101XXXXX` pattern)
- Compatible with the tactile firmware's phase bit mapping (`phase(5 downto 1)`)
- Includes new method `setAmpModulationStep(int step)` for controlling modulation rate

**Why 16 divisions?**
The tactile firmware uses reduced timing resolution:
- Phase: 5 bits instead of 6 bits (16 steps vs 32 steps)
- Counter: 4 bits instead of 5 bits (0-15 vs 0-31)
- Pulse width: 7 cycles instead of 16 cycles

This trade-off allows the FPGA to handle amplitude modulation for tactile feedback.

### 2. GUI Integration
**Modified:** `src/acousticfield3d/gui/panels/TransControlPanel.java`

**Changes:**
- Added import for `SimpleFPGA_Tactile`
- Added "SimpleFPGA Tactile" option to device dropdown (index 7)
- Added case in `getDeviceConnection()` to instantiate tactile protocol

## Usage

### In the GUI:
1. Open AcousticFieldSim
2. In the device dropdown, select **"SimpleFPGA Tactile"**
3. Connect to your serial port
4. The software will now send phase data with 16 divisions instead of 32

### Firmware Compatibility:
- **Primary Firmware:** Use "SimpleFPGA" (32 divisions)
- **Tactile Firmware:** Use "SimpleFPGA Tactile" (16 divisions)
- **Secondary Firmware:** Use "SimpleFPGA" (32 divisions)

## Technical Details

### Phase Mapping Difference
**Primary Firmware:**
```vhdl
phase => phase(5 downto 0)  -- Uses bits 0-5 (6 bits, 0-63 range)
counter => counter(7 downto 3)  -- Uses bits 3-7 (5 bits, 0-31 range)
```

**Tactile Firmware:**
```vhdl
phase => phase(5 downto 1)  -- Uses bits 1-5 (5 bits, 0-31 range)
counter => counter(6 downto 3)  -- Uses bits 3-6 (4 bits, 0-15 range)
```

The bit shift in the tactile firmware effectively divides phase values by 2, which is why we need to send data with 16 divisions instead of 32.

### Amplitude Modulation Commands
The tactile firmware supports special commands for amplitude modulation:

- **Command format:** `0b101XXXXX` (0xA0 - 0xBF)
- **XXXXX:** 5-bit step value (0-31)
- **Higher values:** Faster modulation rate
- **Java method:** `setAmpModulationStep(int step)`

### Common Commands (Both Firmwares)
- **254 (0xFE):** Start phases
- **253 (0xFD):** Swap buffers
- **252 (0xFC):** Multiplex mode toggle
- **251 (0xFB):** Start amplitudes

### Primary-Only Commands
- **192 (0xC0):** Enable primary board
- **193 (0xC1):** Enable secondary board
- **150 (0x96):** Switch off clocks
- **151 (0x97):** Switch on clocks

### Tactile-Only Commands
- **160-191 (0xA0-0xBF):** Set amplitude modulation step (101XXXXX pattern)

## Troubleshooting

### Wrong Emitters Activating?
**Problem:** Using "SimpleFPGA" (32 divisions) with tactile firmware causes wrong emitters to activate.

**Solution:** Switch to "SimpleFPGA Tactile" (16 divisions) in the device dropdown.

### Why does this happen?
The tactile firmware's bit shift (`phase(5 downto 1)`) expects phase values in a different range. When you send 32-division data, the firmware interprets it incorrectly, causing emitter mapping errors.

## Files Modified
1. **NEW:** `src/acousticfield3d/protocols/SimpleFPGA_Tactile.java`
2. **MODIFIED:** `src/acousticfield3d/gui/panels/TransControlPanel.java`

## Backward Compatibility
✅ All existing functionality preserved
✅ Original SimpleFPGA class unchanged
✅ No breaking changes to existing code

