# IFE Sample Model Inventory

> **Source:** `C:\Apps\capella-7.0.1\capella\workspace\In-Flight Entertainment System\In-Flight Entertainment System.capella`
> **Why this exists:** The reviewer pointed out that the original demo script referenced "nose wheel landing gear," which **does not exist** in the IFE model. This document captures what's actually in the model so the Sustainment Demo (Phase 2) uses authentic symptoms.

## Model Theme
The IFE (In-Flight Entertainment) sample model is about **passenger entertainment systems on a commercial aircraft**, including:
- Seat-back TV/audio displays
- Cabin video distribution
- Audio-video streaming and broadcasting
- Cabin Management Unit (CMU)
- Passenger service requests
- Airline-imposed video broadcasts

## Key Components Confirmed in the Model

### Subsystems / Hardware
- Cabin Management Unit (CMU)
- CMU Processor / CMU Touch Screen
- Cabin Video Display Unit
- Cabin Video Player
- Cabin Screen, Cabin Screen Video Splitter
- Cabin HW, Cabin Terminal
- Aircraft Front Servers, Applications Server, Application Server HW/SW
- ASU Processor, ASU SW
- Aircraft Interface, Aircraft-Specific Aircraft Interface
- Network Manager (Aircraft-Specific)
- Cabin Terminal Interactions Manager (Core + Airline-Specific)

### Functions Confirmed
- Acquire Audio Stream from Aircraft
- Adapt Entertainment Service
- Broadcast Audio Announcement / Audio Video Stream / Movies / Safety Instructions Movie / VOD Movie / Live Moving-Map Channel
- Cabin Crew interactions
- Capture Imposed Video Selections
- Capture Passenger Service Selection
- Command Airline-Imposed Video Broadcast
- Command Passenger Services
- Decode Video Packet / Decode Video Packets
- Determine Passenger Service Availability
- Display Audio Interruption Screen on Seat TV
- Display Homepage on Seat TV
- Display Imposed Video Data
- Display Imposed Video Playing Status on Cabin Terminal
- Display VOD Movie Data
- Display Video and Play Audio
- Display Video on Cabin Screen
- Display Video on Seat TV

### Data Items
- Audio File, Audio Packet(s), Audio Signal, Audio Stream Header, Audio Stream
- Audio-Video File, Audio-Video Packet(s), Audio-Video Stream Header, Audio-Video Stream
- Available Airline Video List, Available Imposed Video List, Available Languages
- Available Passenger Service Request, Available Passenger Services List
- Available VOD Movies List
- Aircraft Altitude, Aircraft Direction, Aircraft Position, Aircraft Messages
- Broadcast Audio Video Stream(s), Broadcast Movies, Broadcast Stored Audio and Video
- Chosen Movie

### Actors
- Aircraft, Airline Company, Cabin Crew

## Demo Symptoms — Revised to Match the Actual Model

The original (broken) script had:
> "Nose wheel landing gear is retracting 30 seconds slower than nominal. What should I check?"

The IFE model has **no landing gear**. Use these instead:

### Symptom 1: Passenger Display Failure (replaces landing gear)
> **"The Seat TV in row 14 is rebooting every time we try to play a VOD movie. The cabin crew has reset it twice. Where in the architecture should we look for the root cause?"**

**Why this works:**
- Maps to `Display VOD Movie Data`, `Display Video on Seat TV`, `Cabin Video Display Unit`, `Cabin Video Player`, `Decode Video Packet` — all real elements
- Functional chain: Audio-Video Stream → Decode Video Packet → Display Video on Seat TV
- Components to inspect: Cabin Video Display Unit, Cabin Video Unit SW, Cabin Video Player
- Authentic sustainment-engineer language

### Symptom 2: Audio Drop During Imposed Broadcast
> **"During an airline-imposed safety video broadcast, audio is silent on the cabin terminal but the video plays fine. What functions are involved in audio routing for imposed broadcasts?"**

**Why this works:**
- Maps to `Acquire Audio Stream from Aircraft`, `Broadcast Audio Announcement`, `Display Audio Interruption Screen on Seat TV`
- Tests both `get_functional_chain` and `impact_analysis`
- Components: CMU Audio Path, Audio Packet flow, Cabin Terminal

### Symptom 3 (backup): VOD Catalog Stuck
> **"The Available VOD Movies List on the seat-back display is showing yesterday's titles. The Applications Server was rebooted. Which functions feed that list?"**

**Why this works:**
- Maps to `Available VOD Movies List`, `Determine Passenger Service Availability`, `Applications Server`
- Tests requirements traceability path

## Symptoms to AVOID (would fail on IFE)
- Anything mentioning landing gear, hydraulic, brake, steering, nose wheel, retraction
- Anything mentioning engine, fuel, thrust, throttle
- Anything mentioning APU, ECS, pressurization
- Anything mentioning flight controls, autopilot, FBW

## What This Means for the Demo Script

The demo script in `demo/SUSTAINMENT_DEMO_SCRIPT.md` (to be written in Phase 2 / Week 4) MUST use one of the symptoms above. The "Symptom 1" version is recommended because:
1. Display reboot is a relatable, common consumer-electronics failure
2. Touches the most parts of the model (chain, components, exchanges)
3. The agent has clear "what to inspect" answers using `get_functional_chain` + `impact_analysis`
4. The mention of "row 14" and "cabin crew has reset it twice" creates emotional stakes that the original script lacked

## Validation Done
- Source file scanned: `In-Flight Entertainment System.capella` (XMI)
- Element-name extraction via `grep -oE 'name="[^"]+"'`
- All elements cited above are present in the file
- Cross-checked: NO landing-gear / hydraulic / engine elements exist in this model

## Next Action
When Phase 2 (Week 4) starts, the `SustainmentModeAction.java` example prompts and the `demo/SUSTAINMENT_DEMO_SCRIPT.md` MUST use Symptom 1 or 2 above. Reference this file from the demo script for traceability.
