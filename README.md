# pixelbot
An Android- and Arduino-powered AI bot

 Pixelbot is an AI robot that uses an Android device as a brain - the screen animates its face,
 the front-facing camera is its eye, and the speaker is its mouthpiece. The brain communicates
 with its body via Bluetooth to an Arduino-controlled set of servos and motors. A convolutional neural
 network (powered by TensorFlow) is used for object detection (trained on the COCO dataset). The robot
 speaks and, well, thanks to the input from my 6 yr old, farts.
 
 The robot is built with foam board on an Agent 390 base. I've used a Pixel 1 for the phone (hence the name!). 
 
 Photos: 
 ![Pixelbot](https://github.com/daveyburke/pixelbot/blob/master/Pixelbot.jpg)
 ![Pixelbot Body](https://github.com/daveyburke/pixelbot/blob/master/Pixelbot_Back.jpg)
 
 Schematic diagram:
  ![Pixelbot Schematic](https://github.com/daveyburke/pixelbot/blob/master/Schematic.jpg)
  
 The Arduino code is contained in PixelbotBody.ino
 
 Have fun :)
