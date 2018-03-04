/* Copyright 2018 Dave Burke. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.example.pixelbotbrain;

import java.io.IOException;

/**
 * Controls Pixelbot's body which includes a pan/tilt servo that mounts the phone ("face") and
 * motor controllers, which drive track motors for maneuvering the robot.
 */
public class PixelbotBodyController extends BluetoothArduinoBridge {
    // Move servo protocol: SERVO_MOVE_CMD <pan-postion-degrees>  <tilt-position-degrees>
    private static final byte SERVO_MOVE_CMD = 0;

    // Track servo protocol: SERVO_TRACK_CMD <pan-error> <tilt-error> <drive-enable> <object-size>
    private static final byte SERVO_TRACK_CMD = 1;  // check

    public void moveServoCommand(byte panPos, byte tiltPos) throws IOException {
        byte vals[] = { panPos, tiltPos };
        writeData(SERVO_MOVE_CMD, vals);
    }

    public void trackServo(byte panError, byte tiltError, boolean drive, byte objectSize) {
        byte vals[] = { panError, tiltError, drive == true ? (byte)1 : (byte) 0, objectSize };
        writeData(SERVO_TRACK_CMD, vals);
    }
}
