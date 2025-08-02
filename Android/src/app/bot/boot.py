import network
import time
import webrepl
import socket
import json
import gc
import machine
from machine import Pin, PWM
import time
import math
import _thread
import re

SSID = 'prasthanthi-mesh'
PASSWORD = 'minicooper007'
PORT = 8080

def connect_wifi():
    """Connect to WiFi with error handling for ESP32-C3"""
    try:
        # Initialize WiFi
        wlan = network.WLAN(network.STA_IF)
        
        # Deactivate first to ensure clean state
        wlan.active(False)
        time.sleep(1)
        
        # Activate WiFi
        wlan.active(True)
        time.sleep(2)  # Give WiFi time to initialize
        
        print('WiFi initialized, attempting connection...')
        wlan.connect(SSID, PASSWORD)
        
        # Wait for connection
        while not wlan.isconnected():
            time.sleep(0.1)
            
        print('Connected to WiFi:', wlan.ifconfig())
        return True
        
    except OSError as e:
        print('WiFi Internal Error:', e)
        print('This is a common ESP32-C3 issue. Trying alternative approach...')
        
        # Alternative approach: reset and try again
        try:
            wlan.active(False)
            time.sleep(2)
            wlan.active(True)
            time.sleep(3)
            
            print('Retrying WiFi connection...')
            wlan.connect(SSID, PASSWORD)
            
            # Wait for connection with timeout
            timeout = 20
            while timeout > 0 and not wlan.isconnected():
                time.sleep(0.5)
                timeout -= 1
                
            if wlan.isconnected():
                print('Connected to WiFi on retry:', wlan.ifconfig())
                return True
            else:
                print('Failed to connect after retry')
                return False
                
        except Exception as e2:
            print('Alternative approach also failed:', e2)
            return False
            
    except Exception as e:
        print('Unexpected WiFi error:', e)
        return False
        
# Constants
SERVO_FREQ = 50  # 50 Hz for standard servos
SERVO_MIN_US = 500   # microseconds
SERVO_MAX_US = 2400  # microseconds
SERVO_DELAY_TIME_MS = 35  # Slightly faster for smoother movement

# GPIO pin assignments
ASERVO_PIN = 4  # Turntable - 270 Degrees movement (updated from 160)
BSERVO_PIN = 5  # Claw - 180 Degrees movement (corrected from 160)
CSERVO_PIN = 6  # 270 Degrees movement (corrected from 120)
DSERVO_PIN = 7  # 270 Degrees Movement (corrected from 40)
BEEP_PIN = 9    # Beeper GPIO control

# Current positions of each servo (will be updated during operation)
TURNTABLE_POSITION = 0    # Current turntable position (0-270° range)
CLAW_POSITION = 0         # Current claw position (0-180° range)
ARM_C_POSITION = 0        # Current arm C position (0-270° range)
ARM_D_POSITION = 0        # Current arm D position (0-270° range)

# Initialize servos
servo_a = PWM(Pin(ASERVO_PIN), freq=SERVO_FREQ)
servo_b = PWM(Pin(BSERVO_PIN), freq=SERVO_FREQ)
servo_c = PWM(Pin(CSERVO_PIN), freq=SERVO_FREQ)
servo_d = PWM(Pin(DSERVO_PIN), freq=SERVO_FREQ)


# Servo angle to duty cycle converter for ESP32 PWM
def angle_to_duty_ns(angle):
    """Convert angle (0-180) to duty cycle in nanoseconds (ns)"""
    # Clamp angle to valid range
    angle = max(0, min(180, angle))
    pulse_width_us = SERVO_MIN_US + (angle / 180.0) * (SERVO_MAX_US - SERVO_MIN_US)
    return int(pulse_width_us * 1000)  # Convert us to ns for MicroPython's PWM.duty_ns()

def set_servo_angle(servo, angle):
    """Safely set servo angle with bounds checking"""
    try:
        duty_ns = angle_to_duty_ns(angle)
        servo.duty_ns(duty_ns)
        return True
    except Exception as e:
        print(f"Error setting servo angle: {e}")
        return False

def move(servo, degrees, direction="clockwise", speed=5):
    """
    Move a servo by specified degrees in specified direction at specified speed
    
    Args:
        servo: The servo object to move
        degrees (int): Number of degrees to move (1-270)
        direction (str): "clockwise" or "counterclockwise"
        speed (int): Speed from 1-10 (1=slowest, 10=fastest)
    """
    # Declare global variables at the beginning
    global TURNTABLE_POSITION, CLAW_POSITION, ARM_C_POSITION, ARM_D_POSITION
    
    # Validate inputs
    degrees = max(1, min(270, degrees))  # Clamp to 1-270 degrees
    speed = max(1, min(10, speed))       # Clamp to 1-10 speed
    
    # Determine which servo and get its current position
    if servo == servo_a:
        current_position = TURNTABLE_POSITION
        max_range = 270
        position_var = "TURNTABLE_POSITION"
    elif servo == servo_b:
        current_position = CLAW_POSITION
        max_range = 180
        position_var = "CLAW_POSITION"
    elif servo == servo_c:
        current_position = ARM_C_POSITION
        max_range = 270
        position_var = "ARM_C_POSITION"
    elif servo == servo_d:
        current_position = ARM_D_POSITION
        max_range = 270
        position_var = "ARM_D_POSITION"
    else:
        print("Invalid servo specified")
        return False
    
    # Calculate target position
    if direction.lower() == "clockwise":
        target_position = current_position + degrees
        direction_str = "clockwise"
    elif direction.lower() == "counterclockwise":
        target_position = current_position - degrees
        direction_str = "counterclockwise"
    else:
        print(f"Invalid direction: {direction}. Use 'clockwise' or 'counterclockwise'")
        return False
    
    # Ensure target position is within valid range
    target_position = max(0, min(max_range, target_position))
    
    # Calculate step size based on speed
    # Speed 1 = 1 degree steps, Speed 10 = 10 degree steps
    step_size = speed
    total_steps = abs(target_position - current_position) / step_size
    steps = max(1, int(total_steps))  # At least 1 step
    
    # Calculate delay based on speed (faster speed = shorter delay)
    base_delay = 50  # Base delay in ms
    speed_delay = base_delay - (speed - 1) * 4  # Speed 1=50ms, Speed 10=14ms
    speed_delay = max(10, speed_delay)  # Minimum 10ms delay
    
    print(f"Moving servo {degrees}° {direction_str} at speed {speed}")
    print(f"From {current_position}° to {target_position}° in {steps} steps")
    
    # Move servo in increments
    for i in range(steps + 1):
        # Calculate current angle based on step
        if direction.lower() == "clockwise":
            current_angle = current_position + (step_size * i)
        else:
            current_angle = current_position - (step_size * i)
        
        # Clamp to valid range
        current_angle = max(0, min(max_range, current_angle))
        
        # Map the servo range to 0-180 for the duty cycle function
        mapped_angle = (current_angle / max_range) * 180.0
        
        if set_servo_angle(servo, mapped_angle):
            print(f"Step {i}/{steps}: {current_angle:.1f}°")
        else:
            print(f"Failed to set angle at step {i}")
            return False
        
        # Delay based on speed
        time.sleep_ms(speed_delay)
    
    # Update the position variable
    if servo == servo_a:
        TURNTABLE_POSITION = target_position
    elif servo == servo_b:
        CLAW_POSITION = target_position
    elif servo == servo_c:
        ARM_C_POSITION = target_position
    elif servo == servo_d:
        ARM_D_POSITION = target_position
    
    print(f"Servo movement completed! Final position: {target_position}°")
    return True

def move_simultaneous(movements):
    """
    Move multiple servos simultaneously using threading
    
    Args:
        movements: List of tuples (servo, degrees, direction, speed)
    """
    # Create threads for each movement
    threads = []
    
    def move_thread(servo, degrees, direction, speed):
        """Thread function for individual servo movement"""
        move(servo, degrees, direction, speed)
    
    # Start all movements in separate threads
    for servo, degrees, direction, speed in movements:
        thread = _thread.start_new_thread(move_thread, (servo, degrees, direction, speed))
        threads.append(thread)
    
    # Wait for all threads to complete
    # Note: In MicroPython, we need to use a different approach for synchronization
    # For now, we'll use a simple delay based on the longest movement
    max_steps = 0
    for servo, degrees, direction, speed in movements:
        # Calculate steps for this movement
        if servo == servo_a:
            current_pos = TURNTABLE_POSITION
            max_range = 270
        elif servo == servo_b:
            current_pos = CLAW_POSITION
            max_range = 180
        elif servo == servo_c:
            current_pos = ARM_C_POSITION
            max_range = 270
        elif servo == servo_d:
            current_pos = ARM_D_POSITION
            max_range = 270
        
        if direction.lower() == "clockwise":
            target_pos = current_pos + degrees
        else:
            target_pos = current_pos - degrees
        
        target_pos = max(0, min(max_range, target_pos))
        steps = abs(target_pos - current_pos) / speed
        max_steps = max(max_steps, steps)
    
    # Calculate total delay based on longest movement
    base_delay = 50
    speed_delay = base_delay - (speed - 1) * 4
    speed_delay = max(10, speed_delay)
    total_delay = int(max_steps * speed_delay)
    
    print(f"Waiting {total_delay}ms for all movements to complete...")
    time.sleep_ms(total_delay)
    
    print("All simultaneous movements completed!")

def move_simultaneous_simple(movements):
    """
    Alternative approach: Move servos in very small increments simultaneously
    This provides smoother simultaneous movement without threading complexity
    """
    # Declare global variables at the beginning
    global TURNTABLE_POSITION, CLAW_POSITION, ARM_C_POSITION, ARM_D_POSITION
    
    # Validate and prepare movements
    movement_data = []
    for servo, degrees, direction, speed in movements:
        # Get current position and calculate target
        if servo == servo_a:
            current_position = TURNTABLE_POSITION
            max_range = 270
        elif servo == servo_b:
            current_position = CLAW_POSITION
            max_range = 180
        elif servo == servo_c:
            current_position = ARM_C_POSITION
            max_range = 270
        elif servo == servo_d:
            current_position = ARM_D_POSITION
            max_range = 270
        
        if direction.lower() == "clockwise":
            target_position = current_position + degrees
        else:
            target_position = current_position - degrees
        
        target_position = max(0, min(max_range, target_position))
        
        # Calculate step size and total steps
        step_size = speed
        total_steps = abs(target_position - current_position) / step_size
        steps = max(1, int(total_steps))  # Ensure it's an integer
        
        movement_data.append({
            'servo': servo,
            'current': current_position,
            'target': target_position,
            'step_size': step_size,
            'steps': steps,
            'direction': direction,
            'max_range': max_range
        })
    
    # Find the maximum number of steps needed
    max_steps = max([m['steps'] for m in movement_data])
    max_steps = int(max_steps)  # Ensure it's an integer
    
    # Calculate delay based on speed (use average speed)
    avg_speed = sum([m['step_size'] for m in movement_data]) / len(movement_data)
    base_delay = 50
    speed_delay = base_delay - (avg_speed - 1) * 4
    speed_delay = max(10, int(speed_delay))  # Ensure it's an integer
    
    print(f"Moving {len(movements)} servos simultaneously for {max_steps} steps")
    
    # Move all servos in small increments simultaneously
    for step in range(max_steps + 1):
        for movement in movement_data:
            if step <= movement['steps']:
                # Calculate current angle for this step
                if movement['direction'].lower() == "clockwise":
                    current_angle = movement['current'] + (movement['step_size'] * step)
                else:
                    current_angle = movement['current'] - (movement['step_size'] * step)
                
                # Clamp to valid range
                current_angle = max(0, min(movement['max_range'], current_angle))
                
                # Map to duty cycle
                mapped_angle = (current_angle / movement['max_range']) * 180.0
                
                # Set servo position
                set_servo_angle(movement['servo'], mapped_angle)
        
        # Small delay for smooth movement
        time.sleep_ms(speed_delay)
    
    # Update position variables
    for movement in movement_data:
        if movement['servo'] == servo_a:
            TURNTABLE_POSITION = movement['target']
        elif movement['servo'] == servo_b:
            CLAW_POSITION = movement['target']
        elif movement['servo'] == servo_c:
            ARM_C_POSITION = movement['target']
        elif movement['servo'] == servo_d:
            ARM_D_POSITION = movement['target']
    
    print("Simultaneous movement completed!")

def turn_turntable_180_clockwise():
    """Legacy function - moves turntable 180 degrees clockwise at speed 5"""
    return move(servo_a, 180, "clockwise", 5)

def get_current_positions():
    """Get the current positions of all servos"""
    print(f"Current positions:")
    print(f"Turntable: {TURNTABLE_POSITION}°")
    print(f"Claw: {CLAW_POSITION}°")
    print(f"Arm C: {ARM_C_POSITION}°")
    print(f"Arm D: {ARM_D_POSITION}°")

# Action Functions for Robot Commands
# 
# Flexible Message Format:
# Send commands in any of these formats:
# 1. JSON format: {"action": "command_name"}
# 2. Simple format: action: command_name
# 3. Assignment format: action=command_name  
# 4. Direct command: command_name
#
# Available commands:
# - extend_gripper: Extends gripper arms outward
# - retract_gripper: Retracts gripper arms inward  
# - open_claw: Opens the claw
# - close_claw: Closes the claw
# - turn_table_left: Turns turntable left (counterclockwise)
# - turn_table_right: Turns turntable right (clockwise)
# - move_arms_up: Moves both arms up
# - move_arms_down: Moves both arms down
# - dance: Executes the full dance sequence
#
# Examples:
# {"action": "open_claw"}
# action: open_claw
# action=open_claw
# open_claw
#
def extend_gripper():
    """Extend the gripper by moving arm C and D outward"""
    print("Extending gripper...")
    movements = [
        (servo_c, 45, "clockwise", 3),      # Arm C extends outward
        (servo_d, 45, "clockwise", 3)       # Arm D extends outward
    ]
    move_simultaneous_simple(movements)
    return {"status": "success", "action": "extend_gripper", "message": "Gripper extended"}

def retract_gripper():
    """Retract the gripper by moving arm C and D inward"""
    print("Retracting gripper...")
    movements = [
        (servo_c, 45, "counterclockwise", 3),  # Arm C retracts inward
        (servo_d, 45, "counterclockwise", 3)   # Arm D retracts inward
    ]
    move_simultaneous_simple(movements)
    return {"status": "success", "action": "retract_gripper", "message": "Gripper retracted"}

def open_claw():
    """Open the claw by moving servo B to open position"""
    print("Opening claw...")
    move(servo_b, 90, "clockwise", 3)  # Open claw 90 degrees
    return {"status": "success", "action": "open_claw", "message": "Claw opened"}

def close_claw():
    """Close the claw by moving servo B to closed position"""
    print("Closing claw...")
    move(servo_b, 90, "counterclockwise", 3)  # Close claw 90 degrees
    return {"status": "success", "action": "close_claw", "message": "Claw closed"}

def turn_table_left():
    """Turn the turntable left (counterclockwise)"""
    print("Turning table left...")
    move(servo_a, 45, "counterclockwise", 3)  # Turn left 45 degrees
    return {"status": "success", "action": "turn_table_left", "message": "Table turned left"}

def turn_table_right():
    """Turn the turntable right (clockwise)"""
    print("Turning table right...")
    move(servo_a, 45, "clockwise", 3)  # Turn right 45 degrees
    return {"status": "success", "action": "turn_table_right", "message": "Table turned right"}

def move_arms_up():
    """Move both arms up simultaneously"""
    print("Moving arms up...")
    movements = [
        (servo_c, 30, "counterclockwise", 3),  # Arm C moves up
        (servo_d, 30, "counterclockwise", 3)   # Arm D moves up
    ]
    move_simultaneous_simple(movements)
    return {"status": "success", "action": "move_arms_up", "message": "Arms moved up"}

def move_arms_down():
    """Move both arms down simultaneously"""
    print("Moving arms down...")
    movements = [
        (servo_c, 30, "clockwise", 3),      # Arm C moves down
        (servo_d, 30, "clockwise", 3)       # Arm D moves down
    ]
    move_simultaneous_simple(movements)
    return {"status": "success", "action": "move_arms_down", "message": "Arms moved down"}

def extract_action_from_message(message):
    """Extract action from message using regex patterns"""
    try:
        # Clean the message
        message_clean = message.lower().strip()
        print(f"Extracting action from: {repr(message_clean)}")
        
        # Define regex patterns for different message formats
        patterns = [
            r'"action"\s*:\s*"([^"]+)"',  # JSON format: "action": "command"
            r"'action'\s*:\s*'([^']+)'",  # JSON with single quotes
            r'action\s*:\s*([a-z_]+)',    # Simple format: action: command
            r'action\s*=\s*([a-z_]+)',    # Assignment format: action=command
            r'^([a-z_]+)$'                # Just the command alone
        ]
        
        # Try each pattern
        for pattern in patterns:
            match = re.search(pattern, message_clean)
            if match:
                action = match.group(1).strip()
                print(f"Found action: '{action}' using pattern: {pattern}")
                return action
        
        # If no pattern matches, return None
        print("No action found in message")
        return None
        
    except Exception as e:
        print(f"Error extracting action: {e}")
        return None

def process_command(message):
    """Process message and execute corresponding action"""
    try:
        # Extract action from message
        action = extract_action_from_message(message)
        
        if not action:
            return {"status": "error", "message": "No valid action found in message"}
        
        print(f"Processing action: {action}")
        
        # Map actions to functions
        action_map = {
            'extend_gripper': extend_gripper,
            'retract_gripper': retract_gripper,
            'open_claw': open_claw,
            'close_claw': close_claw,
            'turn_table_left': turn_table_left,
            'turn_table_right': turn_table_right,
            'move_arms_up': move_arms_up,
            'move_arms_down': move_arms_down,
            'dance': lambda: {"status": "success", "action": "dance", "message": "Dance completed"}
        }
        
        # Execute the action if it exists
        if action in action_map:
            if action == 'dance':
                print("Executing dance movement...")
                dance_movement()
                result = {"status": "success", "action": "dance", "message": "Dance completed"}
            else:
                result = action_map[action]()
            print(f"Action '{action}' completed successfully")
            return result
        else:
            available_actions = ", ".join(action_map.keys())
            error_msg = f"Unknown action: {action}. Available actions: {available_actions}"
            print(error_msg)
            return {"status": "error", "message": error_msg}
            
    except Exception as e:
        error_msg = f"Error processing command: {str(e)}"
        print(error_msg)
        return {"status": "error", "message": error_msg}

def dance_movement():
    """Create a dance pattern using all four servos with TRUE simultaneous movements"""
    print("Starting full robot dance with simultaneous movements! 🤖💃")
    
    # Dance sequence 1: Multi-directional wave (TRULY simultaneous)
    print("Dance 1: Multi-directional wave (simultaneous)")
    # Move servos in different directions simultaneously
    movements = [
        (servo_a, 40, "clockwise", 2),      # Turntable clockwise
        (servo_b, 45, "counterclockwise", 2),  # Claw counterclockwise
        (servo_c, 90, "clockwise", 2),      # Arm C clockwise
        (servo_d, 90, "counterclockwise", 2)   # Arm D counterclockwise
    ]
    move_simultaneous_simple(movements)
    time.sleep_ms(500)
    
    # Reverse all directions simultaneously
    movements = [
        (servo_a, 40, "counterclockwise", 2),  # Turntable counterclockwise
        (servo_b, 45, "clockwise", 2),      # Claw clockwise
        (servo_c, 90, "counterclockwise", 2),  # Arm C counterclockwise
        (servo_d, 90, "clockwise", 2)       # Arm D clockwise
    ]
    move_simultaneous_simple(movements)
    time.sleep_ms(500)
    
    # Dance sequence 2: Cross-pattern movements (simultaneous)
    print("Dance 2: Cross-pattern movements (simultaneous)")
    # Create crossing pattern with arms
    movements = [
        (servo_a, 60, "clockwise", 2),      # Turntable clockwise
        (servo_b, 60, "clockwise", 2),      # Claw clockwise
        (servo_c, 120, "counterclockwise", 2),  # Arm C counterclockwise
        (servo_d, 120, "clockwise", 2)       # Arm D clockwise
    ]
    move_simultaneous_simple(movements)
    time.sleep_ms(500)
    
    # Reverse the cross pattern
    movements = [
        (servo_a, 60, "counterclockwise", 2),  # Turntable counterclockwise
        (servo_b, 60, "counterclockwise", 2),  # Claw counterclockwise
        (servo_c, 120, "clockwise", 2),      # Arm C clockwise
        (servo_d, 120, "counterclockwise", 2)   # Arm D counterclockwise
    ]
    move_simultaneous_simple(movements)
    time.sleep_ms(500)
    
    # Dance sequence 3: Dynamic claw and arm coordination (simultaneous)
    print("Dance 3: Dynamic claw and arm coordination (simultaneous)")
    for i in range(3):
        # All servos move in different directions simultaneously
        movements = [
            (servo_b, 90, "clockwise", 2),      # Claw open
            (servo_c, 60, "clockwise", 2),      # Arm C up
            (servo_d, 60, "counterclockwise", 2)   # Arm D down
        ]
        move_simultaneous_simple(movements)
        time.sleep_ms(300)
        
        # Reverse all directions simultaneously
        movements = [
            (servo_b, 90, "counterclockwise", 2),  # Claw close
            (servo_c, 60, "counterclockwise", 2),  # Arm C down
            (servo_d, 60, "clockwise", 2)       # Arm D up
        ]
        move_simultaneous_simple(movements)
        time.sleep_ms(300)
    
    # Dance sequence 4: Spiral pattern demonstration (simultaneous)
    print("Dance 4: Spiral pattern demonstration (simultaneous)")
    # Create spiral-like movement with all servos
    movements = [
        (servo_a, 80, "clockwise", 2),      # Turntable (80° from start)
        (servo_b, 180, "clockwise", 2),     # Claw full range
        (servo_c, 270, "clockwise", 2),     # Arm C full range
        (servo_d, 270, "counterclockwise", 2)   # Arm D full range (opposite direction)
    ]
    move_simultaneous_simple(movements)
    time.sleep_ms(500)
    
    # Reverse spiral pattern
    movements = [
        (servo_a, 80, "counterclockwise", 2),   # Turntable back
        (servo_b, 180, "counterclockwise", 2),  # Claw back
        (servo_c, 270, "counterclockwise", 2),  # Arm C back
        (servo_d, 270, "clockwise", 2)       # Arm D back
    ]
    move_simultaneous_simple(movements)
    time.sleep_ms(500)
    
    # Dance sequence 5: Synchronized multi-directional movements (simultaneous)
    print("Dance 5: Synchronized multi-directional movements (simultaneous)")
    for i in range(3):
        # All servos move in different directions with small movements
        movements = [
            (servo_a, 15, "clockwise", 2),      # Small turntable movement
            (servo_b, 20, "counterclockwise", 2),  # Claw opposite direction
            (servo_c, 30, "clockwise", 2),      # Arm C clockwise
            (servo_d, 30, "counterclockwise", 2)   # Arm D counterclockwise
        ]
        move_simultaneous_simple(movements)
        time.sleep_ms(300)
        
        # Reverse all directions
        movements = [
            (servo_a, 15, "counterclockwise", 2),  # Turntable back
            (servo_b, 20, "clockwise", 2),      # Claw back
            (servo_c, 30, "counterclockwise", 2),  # Arm C back
            (servo_d, 30, "clockwise", 2)       # Arm D back
        ]
        move_simultaneous_simple(movements)
        time.sleep_ms(300)
    
    # Dance sequence 6: Wave pattern with alternating directions (simultaneous)
    print("Dance 6: Wave pattern with alternating directions (simultaneous)")
    # Create wave-like pattern with alternating directions
    for i in range(2):
        # First wave
        movements = [
            (servo_a, 30, "clockwise", 2),      # Turntable
            (servo_b, 30, "counterclockwise", 2),  # Claw opposite
            (servo_c, 45, "clockwise", 2),      # Arm C
            (servo_d, 45, "counterclockwise", 2)   # Arm D opposite
        ]
        move_simultaneous_simple(movements)
        time.sleep_ms(400)
        
        # Second wave (different pattern)
        movements = [
            (servo_a, 30, "counterclockwise", 2),  # Turntable opposite
            (servo_b, 30, "clockwise", 2),      # Claw
            (servo_c, 45, "counterclockwise", 2),  # Arm C opposite
            (servo_d, 45, "clockwise", 2)       # Arm D
        ]
        move_simultaneous_simple(movements)
        time.sleep_ms(400)
    
    # Return all to starting positions smoothly (simultaneous)
    print("Returning all servos to starting positions (simultaneous)")
    movements = [
        (servo_a, 0, "counterclockwise", 2),    # Turntable to starting position
        (servo_b, 0, "counterclockwise", 2),    # Claw to starting position
        (servo_c, 0, "counterclockwise", 2),    # Arm C to starting position
        (servo_d, 0, "counterclockwise", 2)     # Arm D to starting position
    ]
    move_simultaneous_simple(movements)
    time.sleep_ms(500)
    
    print("Full robot dance with TRUE simultaneous movements completed! 🎉🤖")

# Initialize servos without forcing movement
def initialize_servos():
    """Initialize servos without forcing movement - use current positions as 0"""
    print("Initializing servos - using current positions as starting points...")
    
    # Set all position variables to 0 (current servo positions become 0)
    global TURNTABLE_POSITION, CLAW_POSITION, ARM_C_POSITION, ARM_D_POSITION
    TURNTABLE_POSITION = 0
    CLAW_POSITION = 0
    ARM_C_POSITION = 0
    ARM_D_POSITION = 0
    
    # Don't move servos - just set their current positions as 0
    print("Servos initialized! Current positions set as 0° for all servos")
    print("Turntable: 0°, Claw: 0°, Arm C: 0°, Arm D: 0°")


def start_command_server():
    """Start a socket server to listen for robot commands in multiple formats"""
    try:
        addr = socket.getaddrinfo('0.0.0.0', PORT)[0][-1]
        server_socket = socket.socket()
        server_socket.bind(addr)
        server_socket.listen(1)
        
        print('Robot command server listening on port', PORT)
        print('Send robot commands to this device on port', PORT)
        
        while True:
            try:
                # Accept connection
                client_socket, client_addr = server_socket.accept()
                print('Connection from', client_addr)
                
                # Set timeout for receiving data
                #client_socket.settimeout(10.0)
                
                # Receive data
                data = client_socket.recv(1024)
                if data:
                    try:
                        # Decode data and clean it
                        message = data.decode('utf-8').strip()
                        print('Received raw:', repr(message))
                        print('Received length:', len(message))
                        
                        # Additional cleaning for common issues
                        message_clean = message.replace('\r', '').replace('\n', '').strip()
                        print('Cleaned message:', repr(message_clean))
                        
                        # Initialize servos before processing command
                        initialize_servos()
                        time.sleep_ms(1000)  # Wait 1 second after initialization
                        
                        # Process the command using regex pattern matching
                        result = process_command(message_clean)
                        
                        # Send response back to client
                        response = json.dumps(result)
                        try:
                            client_socket.send(response.encode('utf-8'))
                            print('Response sent:', response)
                        except Exception as send_error:
                            print('Error sending response:', send_error)
                            
                    except UnicodeError:
                        error_msg = 'Error: Invalid UTF-8 data received'
                        print(error_msg)
                        error_response = json.dumps({"status": "error", "message": error_msg})
                        try:
                            client_socket.send(error_response.encode('utf-8'))
                        except:
                            pass
                    except Exception as e:
                        error_msg = f'Error processing message: {str(e)}'
                        print(error_msg)
                        if 'message' in locals():
                            print('Failed message:', repr(message))
                        if 'message_clean' in locals():
                            print('Failed cleaned message:', repr(message_clean))
                        error_response = json.dumps({"status": "error", "message": error_msg})
                        try:
                            client_socket.send(error_response.encode('utf-8'))
                        except:
                            pass
                
                # Close client connection
                client_socket.close()
                
                # Garbage collection to free memory
                gc.collect()
                
            except Exception as e:
                print('Server error:', e)
                try:
                    client_socket.close()
                except:
                    pass
                gc.collect()
    except Exception as e:
        print('Failed to start server:', e)

def main():
    """Main function"""
    print('ESP32-C3 Mini Robot Command Server Starting...')
    
    # Connect to WiFi with error handling
    if connect_wifi():
        # Start WebREPL
        try:
            #webrepl.start()
            print('WebREPL started')
        except Exception as e:
            print('WebREPL failed to start:', e)
        
        # Start command server
        print('Starting robot command server...')
        start_command_server()
    else:
        print('WiFi connection failed. Starting WebREPL for debugging...')
        try:
            webrepl.start()
            print('WebREPL started - you can connect to debug WiFi issues')
        except Exception as e:
            print('WebREPL also failed:', e)
            print('Try power cycling the ESP32-C3 Mini')

# Run the main function
main()

 
