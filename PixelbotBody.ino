// Pixelbot Body Code
// Controls L298N motor controller for driving tracks and a pan/tilt servo
// Inspired by https://learn.adafruit.com/pixy-pet-robot-color-vision-follower-using-pixycam/pixy-pet-code
// Dave Burke, 2018

#include <Servo.h>
#include <SoftwareSerial.h>

// HC-06 Bluetooth pins on Arduino
#define BT_TX 10
#define BT_RX 11

// L298N motor controller pins on Arduino
#define ENA_PIN 6
#define IN1_PIN 2 
#define IN2_PIN 3
#define IN3_PIN 4
#define IN4_PIN 5
#define ENB_PIN 13

// Pan/tilt servo pins on Arduino
#define TILT_SERVO_PIN 8
#define PAN_SERVO_PIN 9

// Bluetooth commands
#define SERVO_MOVE_CMD 0
#define SERVO_TRACK_CMD 1

#define MAX_CYCLES_BEFORE_UPDATE 10

// Class to communicate with L298N motor controller
class DriveMotors {
public:
  void setup() {
    pinMode(IN1_PIN, OUTPUT);
    pinMode(IN2_PIN, OUTPUT);
    pinMode(IN3_PIN, OUTPUT);
    pinMode(IN4_PIN, OUTPUT);
    pinMode(ENA_PIN, OUTPUT);
    pinMode(ENB_PIN, OUTPUT);
  }

  void updateLeftMotorSpeed(int32_t speed){
    digitalWrite(IN1_PIN, speed > 0 ? LOW : HIGH);
    digitalWrite(IN2_PIN, speed > 0 ? HIGH : LOW);
    if (abs(speed) < 100) speed = 0;
    analogWrite(ENA_PIN, abs(speed));
  }
  
  void updateRightMotorSpeed(int32_t speed){
    digitalWrite(IN3_PIN, speed > 0 ? LOW : HIGH);
    digitalWrite(IN4_PIN, speed > 0 ? HIGH : LOW);
    if (abs(speed) < 100) speed = 0;
    analogWrite(ENB_PIN, abs(speed));
  }
};

// Class to control pan/tilt servos
class PanTiltServos {
private:
  const int32_t PROPORTIONAL = 100;
  const int32_t DERIVATIVE = 200;
  
  Servo mPanServo;
  Servo mTiltServo;
  int32_t mPanAngle = 90;
  int32_t mTiltAngle = 90;
  int32_t mPanError = 0;
  int32_t mTiltError = 0;
  int32_t mPrevPanError = 0;
  int32_t mPrevTiltError = 0;
  
public:
  void setup() {
    mPanServo.attach(PAN_SERVO_PIN);
    mTiltServo.attach(TILT_SERVO_PIN);
  }
  
  void proportionalDerivativeControl(int32_t panError, int32_t tiltError) {
    mPanError = panError;
    mTiltError = tiltError;
  }
  
  void setAbsolutePosition(int32_t panAngle, int32_t tiltAngle) {
    mPanAngle = panAngle;
    mTiltAngle = tiltAngle;
  }
    
  void update() {      
    mPanAngle += (mPanError * PROPORTIONAL + (mPanError - mPrevPanError) * DERIVATIVE) >> 12;
    mTiltAngle += (mTiltError * PROPORTIONAL + (mTiltError - mPrevTiltError) * DERIVATIVE) >> 12;
    
    if (mPanAngle > 180) mPanAngle = 180;
    if (mPanAngle < 0) mPanAngle = 0;
    if (mTiltAngle > 180) mTiltAngle = 180;
    if (mTiltAngle < 0) mTiltAngle = 0;
    
    char tmp[80];
    sprintf(tmp, "mPanAngle %d, mPanError %d\n", mPanAngle, mPanError);
    Serial.write(tmp);
    
    mPanServo.write(mPanAngle);
    mTiltServo.write(mTiltAngle);
    
    mPrevPanError = mPanError;
    mPrevTiltError = mTiltError; 
  }
  
  int32_t getPanAngle(void) {
    return mPanAngle;
  }
};

// Globals
SoftwareSerial BTSerial(BT_TX, BT_RX);
DriveMotors driveMotors;
PanTiltServos panTiltServos;
int cyclesBeforeUpdate = MAX_CYCLES_BEFORE_UPDATE;
bool drive = false;
int32_t objectSize = 0;

void setup() {
  Serial.begin(9600);
  BTSerial.begin(9600);
  panTiltServos.setup();
  driveMotors.setup();
}

void loop() {
  if (BTSerial.available() >= 2) {
    
    int8_t cmd = BTSerial.read();
    int8_t numVals = BTSerial.read();
    int8_t vals[numVals];
    
    while(BTSerial.available() < numVals);
    
    for (int i = 0; i < numVals; i++) {
      vals[i] = BTSerial.read();
    }
    
    switch(cmd) {
      case SERVO_MOVE_CMD: {
        panTiltServos.setAbsolutePosition(vals[0], vals[1]);
        panTiltServos.update();
        
      }
      case SERVO_TRACK_CMD: {
        cyclesBeforeUpdate = MAX_CYCLES_BEFORE_UPDATE;
        panTiltServos.proportionalDerivativeControl(vals[0], vals[1]); 
        drive = vals[2] == 1 ? true : false;
    
        objectSize += (uint8_t) vals[3];
        objectSize -= objectSize >> 3;  // sliding average, last 8
      }
    }
  }
  
  if (cyclesBeforeUpdate > 0) {
    cyclesBeforeUpdate--;
    panTiltServos.update();
  
    if (drive) {
      char tmp[80];

      // Forward speed decreases as we approach the object (size is larger)
      int32_t forwardSpeed = constrain(200 - (objectSize / 4), -200, 200);  
      forwardSpeed = 0;  // disabled for now
      
      sprintf(tmp, "forwardSpeed %d\n", forwardSpeed);
      Serial.write(tmp);
   
      // Steering differential is proportional to the error times the forward speed
      int32_t angle = 90 - panTiltServos.getPanAngle();
      int32_t differential = (angle * 2 + (angle * forwardSpeed));
      
      sprintf(tmp, "differential %d\n", differential);
      Serial.write(tmp);
      
      // Adjust the left and right speeds by the steering differential.
      int32_t leftSpeed = constrain(forwardSpeed + differential, -255, 255);
      int32_t rightSpeed = constrain(forwardSpeed - differential, -255, 255);
        
      driveMotors.updateLeftMotorSpeed(leftSpeed);
      driveMotors.updateRightMotorSpeed(rightSpeed);
    }
  }
  
  delay(20);  
}
