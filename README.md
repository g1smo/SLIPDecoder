SLIPDECODER // madam data

This is a class that connects to a serial port and interprets OSC or other messages over the serial connection. Messages must be encoded using SLIP encoding (there are libraries for Arduino, etc). It allows for multiple control sources to be read over a serial bus - for instance, multiple knobs connected to an Arduino board. 

The SLIPDecoder object takes 3 arguments = a port or address, baudrate and the number of inputs to be read. 
SLIPDecoder.actions is an array of functions that receive the corresponding input as an argument and are evaluated every time a new value is read. The size of the array is set by the numAddresses argument when creating a new SLIPDecoder. 

note: currently, only sequentially-numbered addresses ("/0", "/1", "/2") will work; it should be simple to modify the classfile to accept different addresses. 

note: There have since been updates to the OSC protocol and some changes might be needed if you have devices using a new protocol - in particular, I think newer OSC messages have an additional byte somewhere.


Configuring your arduino or other microcontroller: 
I have included a sample file that should work on an Arduino Uno. I have also confirmed that it works on an Adafruit Metro Mini 328. I am using the following libraries: OSCBoards.h OSCBundle.h OSCData.h OSCMessage.h OSCTiming.h SLIPEncodedSerial.h SLIPEncodedUSBSerial.h 
I'm pretty sure not all those are necessary, but I only have so much time and this was hacked together in a hurry. 

Currently does not support OSC bundles (each control source must send a separate OSC message)


Example code:

//args = [port or path, baudrate, number of addresses/knobs/whatever] 

k = SLIPDecoder.new("/dev/your_USB_device", 9600, 3);

k.start;

k.actions[0] = {|input| input.postln;};

k.actions[1] = {|input| input.postln;}; //define functions to do something with the incoming data. 

