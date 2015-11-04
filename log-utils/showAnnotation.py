#!/usr/bin/python

#from pylab import *
import matplotlib.pyplot as plt
#import matplotlib.cbook as cbook
import sys

scans = []
scanStops = []
scanStarts = []
phoneInteractive = []
appForeGround = []
annotations = []

scanning = False
logfile = open(sys.argv[1], "r")
for line in logfile:
	columns = line.rstrip().split(' ')
	if (columns[1] == "onScan"):
		scans.append([int(columns[0]), columns[2], int(columns[3]), int(columns[4])])
	elif (columns[1] == "startScan"):
		if (scanning == True):
			print "Error at " + columns[0] + " ?"
		scanning = True
		scanStarts.append(int(columns[0]))
	elif (columns[1] == "stopScan"):
		scanning = False
		scanStops.append(int(columns[0]))
	elif (columns[1] == "phoneInteractive"):
		phoneInteractive.append([int(columns[0]), int(columns[2])])
	elif (columns[1] == "appForeGround"):
		appForeGround.append([int(columns[0]), 1])
	elif (columns[1] == "appBackGround"):
		appForeGround.append([int(columns[0]), 0])
	elif (columns[1] == "setLocation"):
		annotations.append([int(columns[0]), columns[2], float(columns[3]), float(columns[4])])

#	print line.rstrip()
#	for column in line.rstrip().split(' '):
#		print column
logfile.close()

########################################
## Show annotated positions over time ##
########################################

floors = set()
for annotation in annotations:
	floor = annotation[1]
	floors.add(floor)

paths = {}
for floor in floors:
	paths[floor] = [[],[],[]] # each floor has 3 lists: time, x, y

for annotation in annotations:
	time = annotation[0]
	floor = annotation[1]
	x = annotation[2]
	y = annotation[3]
	paths[floor][0].append(time)
	paths[floor][1].append(x)
	paths[floor][2].append(y)


# Plot position over time
for floor in floors:
	plt.figure()
	image = plt.imread(floor + ".png")
	plt.imshow(image)
	plt.grid(True)
	plt.plot(paths[floor][1], paths[floor][2])
	plt.title(floor)


plt.show()
