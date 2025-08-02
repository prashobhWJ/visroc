#!/usr/bin/env python3
"""
Test script for sending JSON commands to the ESP32-C3 robot
Usage: python test_commands.py <robot_ip> [command]
"""

import socket
import json
import sys
import time

# Default robot IP and port
ROBOT_IP = "192.168.1.100"  # Replace with your ESP32-C3 IP address
ROBOT_PORT = 8080

def send_json_command(ip, port, command):
    """Send a JSON command to the robot and return the response"""
    try:
        # Create socket
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(10)  # 10 second timeout
        
        # Connect to robot
        print(f"Connecting to robot at {ip}:{port}...")
        sock.connect((ip, port))
        
        # Prepare JSON message
        json_message = json.dumps({"action": command})
        print(f"Sending: {json_message}")
        
        # Send message
        sock.send(json_message.encode('utf-8'))
        
        # Receive response
        response = sock.recv(1024)
        response_str = response.decode('utf-8')
        print(f"Response: {response_str}")
        
        # Parse response
        try:
            response_data = json.loads(response_str)
            return response_data
        except json.JSONDecodeError:
            return {"status": "error", "message": "Invalid JSON response"}
            
    except Exception as e:
        print(f"Error: {e}")
        return {"status": "error", "message": str(e)}
    finally:
        try:
            sock.close()
        except:
            pass

def main():
    """Main function to test robot commands"""
    
    # Parse command line arguments
    if len(sys.argv) < 2:
        print("Usage: python test_commands.py <robot_ip> [command]")
        print(f"Using default IP: {ROBOT_IP}")
        robot_ip = ROBOT_IP
    else:
        robot_ip = sys.argv[1]
    
    if len(sys.argv) >= 3:
        command = sys.argv[2]
        # Send single command
        result = send_json_command(robot_ip, ROBOT_PORT, command)
        print(f"Result: {result}")
        return
    
    # Available commands
    commands = [
        "extend_gripper",
        "retract_gripper", 
        "open_claw",
        "close_claw",
        "turn_table_left",
        "turn_table_right",
        "move_arms_up",
        "move_arms_down",
        "dance"
    ]
    
    print("Available commands:")
    for i, cmd in enumerate(commands, 1):
        print(f"{i}. {cmd}")
    
    print("\nTesting all commands...")
    
    # Test each command with delay
    for cmd in commands:
        print(f"\n--- Testing: {cmd} ---")
        result = send_json_command(robot_ip, ROBOT_PORT, cmd)
        print(f"Status: {result.get('status', 'unknown')}")
        print(f"Message: {result.get('message', 'no message')}")
        
        # Wait between commands
        if cmd != commands[-1]:  # Don't wait after last command
            print("Waiting 3 seconds before next command...")
            time.sleep(3)
    
    print("\nAll commands tested!")

def test_sequence():
    """Test a sequence of robot movements"""
    print("Testing robot movement sequence...")
    
    sequence = [
        "extend_gripper",
        "open_claw",
        "turn_table_right", 
        "move_arms_down",
        "close_claw",
        "move_arms_up",
        "turn_table_left",
        "retract_gripper"
    ]
    
    for cmd in sequence:
        print(f"\nExecuting: {cmd}")
        result = send_json_command(ROBOT_IP, ROBOT_PORT, cmd)
        if result.get('status') == 'success':
            print(f"✓ {result.get('message')}")
        else:
            print(f"✗ {result.get('message')}")
        time.sleep(2)  # Wait 2 seconds between movements

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "sequence":
        test_sequence()
    else:
        main()