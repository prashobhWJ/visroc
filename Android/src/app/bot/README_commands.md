# Robot JSON Commands

This document describes the JSON command interface for the ESP32-C3 robot controller.

## Overview

The robot listens for JSON messages on port 8080 and responds to specific action commands. Each command controls different servo motors to perform robotic movements.

## JSON Message Format

Send JSON messages in the following format:
```json
{"action": "command_name"}
```

## Available Commands

| Command | Description | Servos Used |
|---------|-------------|-------------|
| `extend_gripper` | Extends gripper arms outward | Servo C & D (45° clockwise each) |
| `retract_gripper` | Retracts gripper arms inward | Servo C & D (45° counterclockwise each) |
| `open_claw` | Opens the claw | Servo B (90° clockwise) |
| `close_claw` | Closes the claw | Servo B (90° counterclockwise) |
| `turn_table_left` | Turns turntable left | Servo A (45° counterclockwise) |
| `turn_table_right` | Turns turntable right | Servo A (45° clockwise) |
| `move_arms_up` | Moves both arms up | Servo C & D (30° counterclockwise each) |
| `move_arms_down` | Moves both arms down | Servo C & D (30° clockwise each) |
| `dance` | Executes full dance sequence | All servos |

## Servo Mapping

- **Servo A (Pin 4)**: Turntable - 270° range
- **Servo B (Pin 5)**: Claw - 180° range  
- **Servo C (Pin 6)**: Arm C - 270° range
- **Servo D (Pin 7)**: Arm D - 270° range

## Example Commands

### Basic Usage
```json
{"action": "open_claw"}
{"action": "turn_table_left"}
{"action": "extend_gripper"}
```

### Response Format
Successful response:
```json
{
  "status": "success",
  "action": "open_claw", 
  "message": "Claw opened"
}
```

Error response:
```json
{
  "status": "error",
  "message": "Unknown action: invalid_command. Available actions: extend_gripper, retract_gripper, open_claw, close_claw, turn_table_left, turn_table_right, move_arms_up, move_arms_down, dance"
}
```

## Testing

Use the provided test script to send commands:

```bash
# Test single command
python test_commands.py 192.168.1.100 open_claw

# Test all commands
python test_commands.py 192.168.1.100

# Test movement sequence
python test_commands.py sequence
```

## Robot Connection

1. Ensure the ESP32-C3 is connected to your WiFi network
2. Note the IP address printed in the console when the robot starts
3. Send TCP socket connections to `<robot_ip>:8080`
4. Send JSON messages as UTF-8 encoded strings

## Movement Parameters

- **Movement Speed**: Set to 3 (medium speed) for all commands
- **Angle Increments**: 
  - Gripper: 45° per action
  - Claw: 90° per action  
  - Table: 45° per action
  - Arms: 30° per action

## Notes

- Servo positions are tracked and movements are relative to current position
- Servos are initialized to position 0° when a command is received
- All movements include boundary checking to prevent servo damage
- Simultaneous movements use the `move_simultaneous_simple()` function for smooth operation