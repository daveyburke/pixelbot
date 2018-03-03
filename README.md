# pixelbot
An Android- and Arduino-powered AI bot

Pixelbot is an AI robot that uses an Android device as a brain - the screen animates its face,
the front-facing camera is its eye, and the speaker is its mouthpiece. The brain communicates
with its body via Bluetooth to an Arduino-controlled set of servos and motors. A convolutional neural
network (powered by TensorFlow) is used for object detection (trained on the COCO dataset). The robot
speaks and, well, thanks to the input from my 6 yr old, farts.

 <img src="https://github.com/daveyburke/pixelbot/blob/master/Pixelbot.jpg" width=400/> <img src="https://github.com/daveyburke/pixelbot/blob/master/Pixelbot_Back.jpg" width=400/>

The robot is built with foam board on an Agent 390 base. I've used a Pixel 1 for the phone (hence the name!). An HC-06 Bluetooth module connects the phone to the Arduino. The head is mounted on a Mallofusa pan/tilt servo rig (but using Futaba S3003 servos). The phone attaches with Volport magnetic car mount.

Schematic diagram:
 <img src="https://github.com/daveyburke/pixelbot/blob/master/Schematic.jpg"/>
  
The Arduino code is contained in PixelbotBody.ino. The BluetoothArduinoBridge.java might be useful for other projects looking at using this setup of an Android as a powerful computer and Arduino for controlling hardware. Also CameraObjectRecognizer and the classes in the com.example.pixelbot.tensorflow package are a useful minimal implementation of object recognition with camera.
 
 Have fun :)
 --Dave
