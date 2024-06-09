#Libraries
import socket    #https://wiki.python.org/moin/UdpCommunication
from flask import Flask
from flask import render_template
from flask import jsonify
from flask import request
import time
import threading
import openai
from PIL import Image
import io
import base64

app= Flask(__name__)

#Parameters
localPort=8080
bufferSize=1024
pingtimeout = 2
changeoutput = 0

#Objects
sock = socket.socket(socket.AF_INET,socket.SOCK_DGRAM)  

class Modules:
        def __init__(self, moisture="0", watersensor="0", time=time.time(), pumpstate="0", scheduletime="", setmoisturelevel=0, completed=0):
                self.moisture = moisture
                self.watersensor = watersensor
                self.time = time
                self.pumpstate = pumpstate
                self.scheduletime = scheduletime
                self.setmoisurelevel = setmoisturelevel
                self.completed = completed

        def update(self, newmois, newwatersen, newtime, newpstate):
                self.moisture = newmois
                self.watersensor = newwatersen
                self.pumpstate = newpstate
                self.time = newtime

        def changeroutine (self, newclock, newSVmois):
                self.scheduletime = newclock
                self.setmoisturelevel = newSVmois
        
        def changestatus(self, state):
                self.completed = state

        def getmois(self):
                return self.moisture

        def getwatersen(self):
                return self.watersensor

        def getlasttime(self):
                return self.time

        def getpumps(self):
                return self.pumpstate

        def getsetmoisvalue(self):
                return self.setmoisturelevel

        def getsetschedule(self):
                return self.scheduletime

        def getstatus(self):
                return self.completed

modulelist = []
addresslist = []
moduleipdict = {}

testrequest = []

@app.route('/test', methods=['GET', 'POST'])
def testing():
    id = request.args.get('module', type=int, default = -1)
    if id > 0 and id < (len(modulelist)+1):
        testrequest.append(id)   
        print("Written")
        testrequest.append(id)
    return jsonify({'status': id})



	

@app.route('/change_schedule', methods=['GET', 'POST'])
def scheduling():
	uid = request.args.get('module', type=int, default = -1)
	setmois = request.args.get ('setvalue', type=int, default = 0)
	setschhour = request.args.get('hour', type=int, default = -1)
	setschmin = request.args.get('minutes', type=int, default = -1)
	if uid > 0 and uid < (len(modulelist)+1) and 72 < setmois and setmois < 255:
		outputtime = ""
		if(setschhour < 10):
			outputtime +="0"	
		outputtime += str(setschhour)
		outputtime += ":"
		if(setschmin < 10):
			outputtime +="0"
		outputtime += str(setschmin)
		print("valid")
		print(outputtime)
		modulelist[uid-1].changeroutine(outputtime, setmois)
		print(modulelist[uid-1].getsetschedule())
		print(modulelist[uid-1].getsetmoisvalue())
	return jsonify({'Schedulemin': setschmin, 'Schedulehour': setschhour, 'uid': uid, 'SV': setmois, })

@app.route('/get_sensor_readings', methods=['GET'])
def goboiiy():
        currentmodule = ""
        currentping = ""
        currentpumpstatus = ""
        currentmoisture = ""
        currentwaterlevel = ""	
       #print(modulelist[0].getpumps())
        current_time = time.strftime('%H:%M')
        for i in range(len(modulelist)):
              currentmodule += str(i+1)
              currentmodule += ","
              currentmoisture += modulelist[i].getmois().decode()
              currentmoisture += ","
              deltatime = (time.time() - modulelist[i].getlasttime())
              print(deltatime)
              if deltatime > pingtimeout:
                   print("GG =(")
                   currentping +="No"
              else:
                   currentping +="Yes"
              currentping += ","
              if modulelist[i].getpumps().decode() == "1":
                   currentpumpstatus += "Running"
              else:
                   currentpumpstatus += "Off"
              currentpumpstatus += ","
              if modulelist[i].getwatersen().decode() == "1":
                   currentwaterlevel += "OK"
              else:
                   currentwaterlevel += "Refill!"
              currentwaterlevel += ","
        return jsonify({'time': current_time , 'modules': currentmodule, 'moisture': currentmoisture, 'ping': currentping, 'pump': currentpumpstatus, 'waterlevel': currentwaterlevel})

def run_UDP():
    # function init
    def init():
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1) #enable broadcasting mode
        sock.bind(('', localPort))
        print("UDP server : {}:{}".format("192.168.68.2",localPort))

    # function main
    def main():
        global modulelist
        global addresslist
        global testrequest
        while True:
            data, addr = sock.recvfrom(bufferSize) # get data
            print("received message: {} form {}".format(data,addr))
            print(data)
            print(addr)
            
            if data == b'OK\x00':
               print("Ack")
               moduleipdict[addr].changestatus(1)
            else:
            	if addr in moduleipdict:
                	print("Hi")
            	else:
                	print("New module detected!")
                	modulelist.append(Modules())
                	addresslist.append(addr)
                	moduleipdict[addr] = modulelist[len(modulelist)-1]

            	humidity = data[1:data.index(b'$')]
            	watersensor = data[data.index(b'$') + 1:data.index(b'?')]
            	pumpstatus = data[data.index(b'?')+1: data.index(b'?')+2]
            	print(humidity)
            	print(watersensor)
            	print(pumpstatus)
            	moduleipdict[addr].update(humidity,watersensor,time.time(),pumpstatus)
            	print(len(modulelist))
		
            	if moduleipdict[addr].getsetschedule() == time.strftime('%H:%M') and moduleipdict[addr].getstatus() == 0:
                        outputwaterins = ""
                        outputwaterins += str(moduleipdict[addr].getsetmoisvalue())
                        outputwaterins += "b1"
                        outputwaterstage = outputwaterins.encode()
                        sock.sendto(outputwaterstage,addr)
                        print(outputwaterstage)
            	elif moduleipdict[addr].getsetschedule() != time.strftime('%H:%M'):
                	moduleipdict[addr].changestatus(0)
            
            if len(testrequest) > 0:
                 print("test...")
                 print(testrequest[0]-1)
                 sock.sendto(b'0b!',addresslist[testrequest[0]-1])
                 testrequest.pop(0)
	
    if __name__ == '__main__':
        init()
        main()
 
if __name__ == '__main__':
    flask_thread = threading.Thread(target=run_UDP)
    flask_thread.start()
    app.run(debug=True, host='192.168.68.2')
