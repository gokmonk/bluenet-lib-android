#!/usr/bin/python

#from pylab import *
import matplotlib.pyplot as plt
import sys

scans = []
scanStops = []
scanStarts = []
phoneInteractive = []
appForeGround = []

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

#	print line.rstrip()
#	for column in line.rstrip().split(' '):
#		print column
logfile.close()

############################################
## Visualize number of scans per interval ##
############################################

# Config
buckets = 20
tStep = 250 # in ms

# Init
endTimes=range(tStep,(buckets+1)*tStep,tStep)
numScans=[]
for i in range(0,buckets+1):
	numScans.append([])

numScansTranspose=[]

# Fill up the data, count number of scans per bucket
i=0
for tStart in scanStarts:
	# Get first stopScan after current startScan
	for tStop in scanStops:
		if (tStop > tStart):
			break
	dt = tStop - tStart
	num = 0
	nums = []
	for j in range(0,buckets):
		while (i<len(scans) and scans[i][0] < tStart+endTimes[j]):
			if (scans[i][0] >= tStart ):
				num += 1
			i += 1

		numScans[j].append(num)
		nums.append(num)

	while (i<len(scans) and scans[i][0] < tStop):
		num += 1
		i += 1
	numScans[buckets].append(num)

	numScansTranspose.append(nums)

# Calculate statistics

numValidScans = 0
relativeNumScans=[]
for i in range(0,buckets):
	relativeNumScans.append([])

averageNumScans=[]
for i in range(0,buckets):
	averageNumScans.append(0)

for j in range(0,len(numScans[0])):
	max = float(numScans[buckets][j])
	if (max > 0):
		numValidScans += 1
		for i in range(0,buckets):
			relativeNumScans[i].append(float(numScans[i][j]) / max)
			averageNumScans[i] += numScans[i][j]

for i in range(0,buckets):
	averageNumScans[i] = float(averageNumScans[i]) / numValidScans
#	print "average number of scans after " + str(endTimes[i]) + "ms: " + str(averageNumScans[i])


# Plot num scans over time
#plt.plot(scanStarts, numScans[buckets], label="all")
#for i in reversed(range(0,buckets)):
#	label = str((i+1)*tStep) + "ms"
#	plt.plot(scanStarts, numScans[i], label=label)
#plt.legend()
#plt.grid(True)

plt.plot(endTimes, numScans[0:buckets])
plt.xlabel('time after startScan (ms)')
plt.ylabel('number of scans')

# Plot average num scans per bucket
plt.figure()
plt.plot(endTimes, averageNumScans)
plt.grid(True)
plt.xlabel('time after startScan (ms)')
plt.ylabel('average number of scans')

plt.show()
