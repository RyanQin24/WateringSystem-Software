#include <SPI.h>
#include <Ethernet.h>
#include <EthernetUdp.h>

int errorled = 9;
int pumptran = 2;
int waterlevelsen = 3;
int pumpoffmode = 0;
//conmfigure mac address
byte mac[] = {0x00, 0xAA, 0xBB, 0xCC, 0xDE, 0x9F};
byte server[] = {192,168,68,2};
int waterlevel = 0;
int sensorstate;
int packetlen;
int pumpstate = 0;
int port = 8080;
int pumpthresh = 0;
String tempthresh = "";
int pumpcmd = 0;
EthernetUDP UDP;
String output = "";


unsigned long currentime = millis();
unsigned long updatetime = 1000;
unsigned long teststart = 0;
unsigned long watertimeout = 24000;
unsigned long runschedule;
unsigned long pumponstamp;
unsigned long pumpoffstamp;
unsigned long cycletime = 1000;

void setup() {
  Ethernet.init(10);
  Serial.begin(115200);
  pinMode(errorled, OUTPUT);
  pinMode(pumptran, OUTPUT);
  pinMode(waterlevelsen, INPUT_PULLUP);
  digitalWrite(errorled, HIGH);
  digitalWrite(pumptran, pumpstate);
  Serial.println("Initialize Ethernet with DHCP:");

  //checking for bad startup condition
  while(Ethernet.begin(mac) == 0 || Ethernet.hardwareStatus() == EthernetNoHardware|| Ethernet.linkStatus() == LinkOFF){
  }
  // print your local IP address:

  Serial.print("My IP address: ");
  Serial.println(Ethernet.localIP());
  Serial.println("connected");
  digitalWrite(errorled, LOW);
  UDP.begin(port);
  
}



void loop() {

  //ethernet maintain conditions
  // 1 - renew fail
  // 3 - rebind fail
  while (Ethernet.maintain()== 1 || Ethernet.maintain() == 3  ){
    digitalWrite(errorled, HIGH);
    pumpstate = 0;
    digitalWrite(pumptran, pumpstate);
    Serial.println("Error");
  }
  if (pumpcmd == 1){
    int result = map(analogRead(A2),0,1023,0,255);
    if (result <= pumpthresh || digitalRead(waterlevelsen) == 0|| (millis() - runschedule) > watertimeout){
      pumpstate = 0;
      digitalWrite(pumptran, pumpstate);
      Serial.println("Stopped sch");
      pumpcmd = 0;
    }else{
      if ((millis()-pumponstamp) < cycletime){
        pumpstate = 1;
        digitalWrite(pumptran, pumpstate);
        Serial.println("Sch running");
      }else if (pumpoffmode == 0){
        pumpoffstamp = millis();
        pumpoffmode = 1;
      }
      if (pumpoffmode == 1 && (millis() - pumpoffstamp) < cycletime){
        pumpstate = 0;
        digitalWrite(pumptran, pumpstate);
        Serial.println("Sch off DC");
      }else if (pumpoffmode == 1){
        pumponstamp = millis();
        pumpoffmode = 0;
      }
    }
  }else if (pumpcmd == 2){
    if (millis() - teststart < updatetime && digitalRead(waterlevelsen) == 1){
        pumpstate = 1;
        digitalWrite(pumptran, pumpstate);
    }else{
        pumpcmd = 0;
        pumpstate = 0;
        digitalWrite(pumptran, pumpstate);
    }
  }
  if(millis() - currentime > updatetime){
  digitalWrite(errorled, LOW);
  waterlevel = map(analogRead(A2),0,1023,0,255);
  sensorstate = digitalRead(waterlevelsen);
  output += "*";
  output += waterlevel;
  output += "$";
  output += sensorstate;
  output += "?";
  output += pumpstate;
  char outstage[output.length()+1] = {};

  output.toCharArray(outstage, output.length()+1);
  UDP.beginPacket(server,port);
    UDP.write(outstage,output.length()+1);
    UDP.endPacket();
  output = "";
  currentime = millis();
  }
  packetlen = UDP.parsePacket();
  if (packetlen > 0){
    Serial.println(packetlen);
    char tempread[packetlen] = {};
    UDP.read(tempread, packetlen);
    if (tempread[packetlen-1] == '1'){
      Serial.println("Pump command");
      pumpcmd = 1;
      runschedule = millis();
      pumponstamp = millis();
      UDP.beginPacket(server,port);
        UDP.write("OK",3);
        UDP.endPacket();
    }else if (tempread[packetlen-1] == '!'){
      Serial.println("Testing");
      pumpcmd = 2;
      teststart = millis();
    }
    for (int i = 0; i < packetlen-2;i++){
      tempthresh += tempread[i];
    }
    pumpthresh = tempthresh.toInt();
    tempthresh = "";
    Serial.println(pumpthresh);
  }

}
