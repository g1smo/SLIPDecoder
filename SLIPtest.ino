
#include <OSCBoards.h>
#include <OSCBundle.h>
#include <OSCData.h>
#include <OSCMatch.h>
#include <OSCMessage.h>
#include <OSCTiming.h>
#include <SLIPEncodedSerial.h>
SLIPEncodedSerial SLIPSerial(Serial);
#include <SLIPEncodedUSBSerial.h>

const int smoothSize = 20;
int val0[smoothSize], val1[smoothSize], val2[smoothSize];

bool checkFlutter(int input[]) {
  boolean result = true;
  for (int i = 1; i <=(smoothSize-1); i++) {
    if(input[0] == input[i]) {result = false;} else {}; 
  }
  return result;

};

void setup() {
  Serial.begin(9600);
  while(!Serial);
}


void loop() {
  for (int i = (smoothSize-1); i >=1; i --) {
    val0[i] = val0[i-1];
    val1[i] = val1[i-1];
    val2[i] = val2[i-1];
  };
  val0[0] = analogRead(0);
  val1[0] = analogRead(1);
  val2[0] = analogRead(2);
  OSCMessage msg0("/0");
  if (checkFlutter(val0)) { //check if the value is the same as the last couple
  msg0.add(val0[0]);
  msg0.send(SLIPSerial); // send the bytes to the SLIP stream
  SLIPSerial.endPacket(); // mark the end of the OSC Packet
  msg0.empty(); // free space occupied by message
  };

  if(checkFlutter(val1)) {
  OSCMessage msg1("/1");
  msg1.add(val1[0]);
  msg1.send(SLIPSerial);
  SLIPSerial.endPacket();
  msg1.empty();
  };

  if(checkFlutter(val2)) {
  OSCMessage msg1("/2");
  msg1.add(val2[0]);
  msg1.send(SLIPSerial);
  SLIPSerial.endPacket();
  msg1.empty();
  };
  delay(30);
}
