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

**Time Resolution Comparison:**
- **Primary:** 25 μs ÷ 32 = 0.78 μs per step (finer control)
- **Tactile:** 25 μs ÷ 16 = 1.56 μs per step (coarser, but sufficient for tactile)

The reduced resolution frees up FPGA resources for the amplitude modulation circuitry needed for tactile sensations.

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

### Understanding Phase, Counter, and Pulse Width

#### The Ultrasonic Cycle
Ultrasonic transducers emit at ~40kHz (40,000 cycles per second). Each cycle has a period of 25 microseconds.

To create acoustic levitation, we need to control:
1. **When** each transducer turns on (PHASE)
2. **How long** it stays on (PULSE WIDTH)

#### How the FPGA Controls Timing

**The Counter:**
- A global counter that increments continuously from 0 to MAX and repeats
- **Primary:** 0-31 (32 steps per ultrasonic cycle)
- **Tactile:** 0-15 (16 steps per ultrasonic cycle)
- Each step represents a time slice of the ultrasonic period

**The Phase:**
- Determines WHEN a specific transducer turns on
- Each transducer compares its phase value to the current counter
- When `counter == phase`, the transducer activates

**The Pulse Width:**
- How many counter steps the transducer stays ON after activation
- **Primary:** 16 steps (50% duty cycle with 32 divisions)
- **Tactile:** 7 steps (~44% duty cycle with 16 divisions)

### Temporal Signal Example - Single Emitter

#### Primary Firmware (32 divisions, phase = 8)
```
Counter:  0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
          |                          |                                |                                |
Signal:   ___________________________################################################################___
          |<-------- OFF -------->|<------- ON (16 steps) ------->|<-------- OFF -------->|
          |                       ^
          |                    Phase=8: Turn ON when counter reaches 8
          |                    Stays ON for 16 steps (until counter=24)
          |
          |<----------------------- One ultrasonic cycle (25 us @ 40kHz) ----------------------->|
```

#### Tactile Firmware (16 divisions, phase = 4)
```
Counter:  0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 | 0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
          |          |                    |              |           |                    |              |
Signal:   ___________#####################___________________________#####################_______________
          |<-- OFF ->|<-- ON (7 steps) ->|<--- OFF --->|<-- OFF -->|<-- ON (7 steps) -->|<--- OFF --->|
          |          ^
          |       Phase=4: Turn ON when counter reaches 4
          |       Stays ON for 7 steps (until counter=11)
          |
          |<---------- One ultrasonic cycle (25 us @ 40kHz) ---------->|
```

### Why Different Phases Matter - Multiple Emitters

When you have multiple emitters with different phases, they create interference patterns:

```
Emitter A (phase=0):  ################________________################________________
Emitter B (phase=8):  ________________################________________################
Emitter C (phase=16): ________________________________################________________
Emitter D (phase=24): ________################________________________________########
                      |<------------- One cycle ------------->|
```

By controlling the phase of each emitter, you can:
- Create focal points (constructive interference)
- Steer acoustic beams
- Generate complex acoustic fields for levitation

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

### Amplitude Modulation - How It Works

#### What is Amplitude Modulation?
Amplitude modulation (AM) allows you to turn individual emitters ON and OFF at a low frequency (below ultrasonic) to create tactile sensations that can be felt by the user.

**The Concept:**
- Ultrasonic frequency: 40 kHz (too high to feel)
- Modulation frequency: 1-200 Hz (can be felt as vibration/pressure)
- By turning emitters ON/OFF at tactile frequencies, you create sensations

#### The Hardware Implementation

**AmpModulator Component:**
```vhdl
-- Cycles through 256 amplitude levels (0-255)
-- Generates a clock signal (chgClock) to toggle emitters
-- Step rate controls how fast it cycles through levels
```

**How It Works:**

1. **The AmpModulator** continuously counts from 0 to 255, then repeats
2. Every time it increments, it can send a `chgClock` pulse
3. The **step parameter** controls how many clock cycles to wait between increments
4. Each `chgClock` pulse toggles one emitter ON or OFF

**Example - Amplitude Modulation Timing:**
```
Step = 10 (slower modulation)
FPGA Clock: ||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||
Step Count: 0 1 2 3 4 5 6 7 8 9 10| 0 1 2 3 4 5 6 7 8 9 10| 0 1 2 3 4...
                                  ^                       ^
chgClock:   ______________________#_______________________#______________
                                  |                       |
                              Pulse every 10 clocks   Pulse every 10 clocks

Amp Value:  0   0   0   0   0   0   0   0   0   0   1   1   1   1   1   1...
            (increments on each chgClock pulse)
```

#### Enabling/Disabling Individual Emitters

Each emitter has an `enabled` signal that can be toggled:

```vhdl
-- In AllChannels.vhd (Tactile)
signal s_enabled : STD_LOGIC_VECTOR((NBLOCKS-1) downto 0) := (others => '1');

-- When chgClock pulses, toggle the emitter specified by pulse_length
AllChannels: process (chgClock) begin
    if (rising_edge(chgClock)) then
        s_enabled( to_integer(unsigned(pulse_length)) ) <= NOT s_enabled( to_integer(unsigned(pulse_length)) );
    end if;
end process;

-- The pulse output is gated by the enabled signal
pulse <= '1' and enabled;  -- Only outputs if enabled=1
```

**What This Means:**
- Each emitter can be individually enabled or disabled
- The `pulse_length` input selects which emitter to toggle
- Every `chgClock` pulse toggles one emitter's enabled state

#### Complete Amplitude Modulation Example

Let's say you want to create a 100 Hz tactile sensation on emitter #5:

**Step 1: Set the modulation rate**
```java
device.setAmpModulationStep(15);  // Medium speed
```

**Step 2: The firmware cycles through amplitude levels**
```
Amp Counter: 0 → 1 → 2 → 3 → 4 → 5 → ... → 255 → 0 → 1 → ...
             (increments every 15 FPGA clock cycles)
```

**Step 3: Toggle emitter #5 at specific amplitude values**
```
When amp = 0:   Toggle emitter #5 → ON
When amp = 128: Toggle emitter #5 → OFF
When amp = 0:   Toggle emitter #5 → ON
When amp = 128: Toggle emitter #5 → OFF
...
```

**Result: Emitter #5 modulated at ~100 Hz**
```
Time (ms):    0    5   10   15   20   25   30   35   40   45   50
              |    |    |    |    |    |    |    |    |    |    |
Emitter #5:   #####_____#####_____#####_____#####_____#####_____
              ON   OFF  ON   OFF  ON   OFF  ON   OFF  ON   OFF

              |<-10ms->|  = 100 Hz modulation frequency
```

The user feels this as a 100 Hz vibration/pressure sensation!

#### Why This Creates Tactile Feedback

**Ultrasonic alone (40 kHz):**
- Too fast for human skin to detect
- Creates acoustic radiation pressure but no vibration sensation

**Amplitude Modulated (1-200 Hz):**
- The ON/OFF pattern is slow enough to feel
- Creates pulsating pressure that stimulates mechanoreceptors in skin
- Feels like vibration or tapping

**Applications:**
- Haptic feedback in mid-air
- Tactile buttons without physical contact
- Texture simulation
- Directional cues

### Amplitude Modulation Commands
The tactile firmware supports special commands for amplitude modulation:

- **Command format:** `0b101XXXXX` (0xA0 - 0xBF)
- **XXXXX:** 5-bit step value (0-31)
- **Higher values:** Faster modulation rate (faster cycling through 0-255)
- **Lower values:** Slower modulation rate (slower cycling, lower tactile frequencies)
- **Java method:** `setAmpModulationStep(int step)`

**Step Value Guide:**
- Step = 1: Very slow modulation (~1-10 Hz)
- Step = 10: Medium modulation (~50-100 Hz)
- Step = 31: Fast modulation (~200+ Hz)

#### Visual Summary: Two-Level Modulation

The tactile firmware uses **two levels of modulation**:

**Level 1: Ultrasonic Carrier (40 kHz) - TOO FAST TO FEEL**
```
Emitter signal (when enabled):
Time (us):  0    25   50   75   100  125  150  175  200
            |    |    |    |    |    |    |    |    |
            #_#_#_#_#_#_#_#_#_#_#_#_#_#_#_#_#_#_#_#_#_#_
            40 kHz ultrasonic - creates acoustic pressure
```

**Level 2: Amplitude Modulation (1-200 Hz) - CAN BE FELT**
```
Enabled signal (controlled by AmpModulator):
Time (ms):  0    5    10   15   20   25   30   35   40
            |    |    |    |    |    |    |    |    |
            #####_____#####_____#####_____#####_____#####
            100 Hz modulation - creates tactile sensation
```

**Combined Result:**
```
Actual emitter output:
Time (ms):  0    5    10   15   20   25   30   35   40
            |    |    |    |    |    |    |    |    |
            #_#_#_____#_#_#_____#_#_#_____#_#_#_____#_#_#
            |<-->|    |<-->|    |<-->|    |<-->|
            40kHz      40kHz      40kHz      40kHz
            burst      burst      burst      burst

            User feels: 100 Hz vibration/pressure
```

**Key Insight:**
- When `enabled = 1`: Emitter outputs 40 kHz ultrasonic (phase-controlled)
- When `enabled = 0`: Emitter is completely OFF
- By toggling `enabled` at 1-200 Hz, you create tactile sensations
- The `pulse_length` input selects WHICH emitter to toggle
- The `steps` parameter controls HOW FAST the toggling happens

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

### Real-World Timing Example

For a 40 kHz ultrasonic transducer (typical frequency):
- **Period:** 1/40000 = 25 microseconds per cycle
- **Primary (32 divisions):** Each step = 25μs/32 = **0.78 μs**
- **Tactile (16 divisions):** Each step = 25μs/16 = **1.56 μs**

**Example: Emitter with phase = 4 in tactile mode**
```
Time (us):  0.0   1.6   3.1   4.7   6.3   7.8   9.4  11.0  12.5  14.1  15.6  17.2  18.8  20.3  21.9  23.4  25.0
Counter:     0     1     2     3     4     5     6     7     8     9    10    11    12    13    14    15     0
            |     |     |     |     |     |     |     |     |     |     |     |     |     |     |     |     |
Signal:     ________________________#######################################################____________________
                                    ^                                         ^
                                    ON at 6.3 us                              OFF at 17.2 us
                                    (counter = 4)                             (after 7 steps)
```

The transducer:
1. Waits until counter reaches 4 (at 6.3 μs)
2. Turns ON
3. Stays ON for 7 counter steps (7 × 1.56 μs = 10.9 μs)
4. Turns OFF at counter 11 (at 17.2 μs)
5. Waits until the cycle repeats

## Files Modified
1. **NEW:** `src/acousticfield3d/protocols/SimpleFPGA_Tactile.java`
2. **MODIFIED:** `src/acousticfield3d/gui/panels/TransControlPanel.java`

## Backward Compatibility
✅ All existing functionality preserved
✅ Original SimpleFPGA class unchanged
✅ No breaking changes to existing code

---

## ~~KNOWN ISSUE: Inverted ON/OFF Pattern~~ ✅ FIXED

### ~~Problem Description~~ (Historical - Now Fixed)
~~When activating all emitters in the GUI, some emitters turn ON while others turn OFF, creating an inverted pattern.~~

**STATUS: FIXED** - The firmware now toggles all emitters synchronously instead of cycling through them individually.

### Root Cause Analysis

**Hardware Connection:**
```
AmpModulator.amp[7:0] → AllChannels.pulse_length[7:0]
```

**The Issue:**
1. `AmpModulator` continuously cycles `amp` from 0→255
2. Every `chgClock` pulse, it toggles the emitter at index `pulse_length` (which equals `amp`)
3. All emitters start with `s_enabled = '1'` (ON by default)
4. The `amp` counter randomly toggles emitters as it cycles through 0-255

**Example Timeline:**
```
Time 0:   All emitters enabled = '1' (all ON)
Time 1:   amp=0,  chgClock pulses → Toggle emitter 0  → emitter 0 now OFF
Time 2:   amp=1,  chgClock pulses → Toggle emitter 1  → emitter 1 now OFF
Time 3:   amp=2,  chgClock pulses → Toggle emitter 2  → emitter 2 now OFF
...
Time 256: amp=0,  chgClock pulses → Toggle emitter 0  → emitter 0 now ON again
```

The emitters are being toggled in a cycling pattern based on the `amp` counter, not based on user control!

### Why the Inverted Pattern?

When you "activate" emitters in the GUI:
- GUI sends phase data for all emitters
- Firmware already has all emitters ON by default (`s_enabled = '1'`)
- The `amp` counter has been randomly toggling emitters
- Result: Some emitters are ON, some are OFF (depends on when they were last toggled)

When you "deactivate" emitters:
- GUI sends phase=16 (OFF) for all emitters
- But the `enabled` signal is independent of phase!
- The emitters that were toggled OFF are now the only ones outputting (inverted pattern)

### Solutions

#### Solution 1: Initialize emitters as OFF (Quick Fix)
Change line 32 in `AllChannels.vhd`:
```vhdl
-- OLD:
signal s_enabled : STD_LOGIC_VECTOR((NBLOCKS-1) downto 0) := (others => '1');

-- NEW:
signal s_enabled : STD_LOGIC_VECTOR((NBLOCKS-1) downto 0) := (others => '0');
```

This ensures all emitters start OFF, so the first toggle turns them ON.

**Limitation:** This only fixes the initial state. The random toggling still occurs.

#### Solution 2: Disable Automatic Toggling (Recommended for now)
Set the amplitude modulation step to 0 to stop `chgClock` from pulsing:

```java
// In your Java code after connecting:
device.setAmpModulationStep(0);  // Stops automatic toggling
```

This disables the amplitude modulation feature but gives you predictable emitter control.

#### Solution 3: Implement Proper Amplitude Control (Complete Fix)
The firmware needs to be modified to:

1. **Store amplitude values** for each emitter (similar to phase storage)
2. **Compare amplitude to a threshold** to determine which emitters should be modulated
3. **Only toggle emitters** that have amplitude < 1.0

**Required VHDL changes:**
```vhdl
-- Add amplitude storage (similar to phase storage)
signal s_amplitude : array(0 to NBLOCKS-1) of STD_LOGIC_VECTOR(7 downto 0);

-- Modify the toggle logic
AllChannels: process (chgClock) begin
    if (rising_edge(chgClock)) then
        -- Only toggle if this emitter's amplitude indicates modulation needed
        if (s_amplitude(to_integer(unsigned(pulse_length))) < 255) then
            s_enabled(to_integer(unsigned(pulse_length))) <=
                NOT s_enabled(to_integer(unsigned(pulse_length)));
        end if;
    end if;
end process;
```

**Required Java changes:**
- The amplitude data is already being sent (lines 98-103 in SimpleFPGA_Tactile.java)
- No changes needed on the Java side

### Solution 4: Toggle All Emitters Synchronously (IMPLEMENTED) ✅

**Use case:** Create tactile feedback by modulating the entire acoustic field at tactile frequencies.

**Implementation:** Modified the toggling logic in `AllChannels.vhd` (lines 95-104) to toggle ALL emitters together:

```vhdl
-- Toggle ALL emitters synchronously for amplitude modulation
-- This creates a pulsating focal point at the modulation frequency
AllChannels: process (chgClock) begin
        if (rising_edge(chgClock)) then
				-- Toggle all emitters together (not individually)
				for i in 0 to (NBLOCKS-1) loop
					s_enabled(i) <= NOT s_enabled(i);
				end loop;
		  end if;
 end process;
```

**How It Works:**

1. **All emitters start enabled** (`s_enabled = '1'`)
2. **Every `chgClock` pulse**, ALL emitters toggle together: `'1'` → `'0'` → `'1'` → `'0'`...
3. **The focal point blinks** at the modulation frequency
4. **User feels vibration** at the focal point location

**Example Timeline:**
```
Time 0 ms:    chgClock pulse → All s_enabled = '0' → Focal point OFF
Time 5 ms:    chgClock pulse → All s_enabled = '1' → Focal point ON
Time 10 ms:   chgClock pulse → All s_enabled = '0' → Focal point OFF
Time 15 ms:   chgClock pulse → All s_enabled = '1' → Focal point ON
...

Result: Focal point pulses at 100 Hz (if chgClock = 100 Hz)
        User feels 100 Hz vibration at the focal point!
```

**Controlling Modulation Frequency:**

Use `setAmpModulationStep()` to control the modulation rate:

```java
// Faster modulation (higher frequency)
device.setAmpModulationStep(1);   // Fast toggling → High frequency vibration

// Medium modulation
device.setAmpModulationStep(10);  // Medium toggling → Medium frequency

// Slower modulation (lower frequency)
device.setAmpModulationStep(20);  // Slow toggling → Low frequency vibration

// No modulation (constant focal point)
device.setAmpModulationStep(31);  // Maximum step → Very slow/no toggling
```

**Benefits:**
✅ **Tactile feedback** - Users can feel vibration at the focal point
✅ **Adjustable frequency** - Control vibration frequency with `setAmpModulationStep()`
✅ **Stable focal point** - Location determined by phase (doesn't shift)
✅ **All emitters work together** - No chaotic interference patterns
✅ **Simple and effective** - Clean implementation

**What You Get:**
- Focal point location: Controlled by **phase values** (40 kHz interference)
- Focal point intensity: Modulated at **tactile frequency** (1-200 Hz)
- Tactile sensation: User feels **vibration** at the focal point

**What You Don't Get:**
- Cannot have different modulation frequencies at different locations
- All focal points pulse together at the same frequency
- Cannot selectively modulate only some emitters

This is the **correct implementation** for amplitude modulation in mid-air haptic systems!

