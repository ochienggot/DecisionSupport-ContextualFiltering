#script (python)

from gringo import Fun

import sys
import re
import time
import datetime

#from twisted.python import log

class Route(object):
	"""docstring for Route"""
	def __init__(self, points, distance, time):
		super(Route, self).__init__()
		self.points = points
		self.distance = distance
		self.time = time
		self.pollutionValues = None

routes = None
# pollutionValues = None

def get_routes(starting_point, ending_point, cost_mode, number_of_routes):
	try:
		starting_time = time.time()
		#starting_time_clock = time.clock()
		#tarting_time_time = time.time()

		initRoutes(starting_point, ending_point, cost_mode, number_of_routes)

		output = []

		if routes is not None :
			routeNumber = 1
			for route in routes :
				stepNumber = 1
				for point in route.points :
					output.append((routeNumber, stepNumber, point))
					stepNumber += 1
				routeNumber +=1

		# r = Fun('r',output)
		# return r
		#print ("TIME get_routes: %f;%f" % (time.clock() - starting_time_clock, time.time() - starting_time_time))
		ending_time = time.time()
		print ("GET_ROUTE_START_TIME: {}" .format(starting_time))
		print ("GET_ROUTE_END_TIME: {}" .format(ending_time))
		print ("GET_ROUTE_PROCESSING_TIME(milisecond): %f" %((ending_time-starting_time)*1000.0))
		return output
	except Exception, e:
		print('ERROR get_routes: %s' % str(e))
		return []

def get_routes_data(starting_point, ending_point, cost_mode, number_of_routes):
	try:
		starting_time_clock = time.clock()
		starting_time_time = time.time()

		initRoutes(starting_point, ending_point, cost_mode, number_of_routes)

		output = []

		if routes is not None :
			routeNumber = 1
			for route in routes :
				# print((routeNumber, route.time, route.distance))
				output.append((routeNumber, route.time, route.distance))
				routeNumber +=1
				
		#print ("TIME get_routes_data: %f;%f" % (time.clock() - starting_time_clock, time.time() - starting_time_time))
		print ("get_routes_data_TIME: %f" % ((time.time() - starting_time_time)*1000.0))
		return output
	except Exception, e:
		print('ERROR get_routes_data: %s' % str(e))
		return []

def get_max_pollution(routeID):
	try:
		starting_time_clock = time.clock()
		starting_time_time = time.time()
		
		if routes is not None :
			if routeID <= len(routes) :
				route = routes[routeID-1]
				initPollutionValues(route)
				if route.pollutionValues is not None :
					#print ("TIME get_max_pollution: %f;%f" % (time.clock() - starting_time_clock, time.time() - starting_time_time))
					print ("get_max_pollution_TIME: %f" % ((time.time() - starting_time_time)*1000.0))
					return max(route.pollutionValues)
			else :
				raise ValueError('Wrong routeID in get_max_pollution')
		else :
			raise ValueError('get_max_pollution is called before routes is initialized')
	except Exception, e:
		print('ERROR get_max_pollution: %s' % str(e))
		return 0


def initRoutes(starting_point, ending_point, cost_mode, number_of_routes):
	global routes

	if routes is None :
		routes = []

		#log.startLogging(sys.stdout)
		starting_time_time = time.time()

		print("Route: FROM %s TO %s" % (starting_point, ending_point))

		sSplit = starting_point.strip('"').split()
		eSplit = ending_point.strip('"').split()

		# print("strip %s" % (sSplit))
		# print("strip %s" % (eSplit))

		if len(sSplit) != 2 :
			raise ValueError('Wrong starting point')
		if len(eSplit) != 2 :
			raise ValueError('Wrong ending point')

		srcXlon = sSplit[0]
		srcYlat = sSplit[1]
		trgXlon = eSplit[0]
		trgxlat = eSplit[1]
		
		# data validation
		float(srcXlon)
		float(srcYlat)
		float(trgXlon)
		float(trgxlat)

		costMode = cost_mode.lower().strip('"')
		numberOfRoutes = number_of_routes

		from websocket import create_connection
		ws = create_connection("ws://localhost:7686")
# 		ws = create_connection("ws://131.227.92.55:7686")
		print ("Created connection, time = %f" %(time.time() - starting_time_time))
		result =  ws.recv()
		# print "Received '%s'" % result
		ws.send((u"getCityRoute(%s,%s,%s,%s,%s,%d)" % (srcXlon,srcYlat,trgXlon,trgxlat,costMode,numberOfRoutes)).encode('utf8'))
		print "Sent request"
		for x in xrange(numberOfRoutes):
			# print "Reciving..."
			result =  ws.recv()
			print "Received '%s'" % result
			p = re.compile('\w+;\w+;\w+;\w+\n(LINESTRING|MULTILINESTRING)\((.+)\);(\d+);(\d+);(\d+.\d+)')
			m = p.match(result.decode('utf8'))
			if m :
				# print(m.group(1),m.group(3),m.group(4),m.group(5))

				if m.group(1) == u'LINESTRING' :
					# print(m.group(1))
					route = Route(m.group(2).encode().split(','),int(m.group(3)),int(m.group(4)))
			
					routes.append(route)
		ws.close()

	else :
		print("initRoutes already called")

def initPollutionValues(route):
	global routes

	if route.pollutionValues is None :

		# print("Not None %d" % (route.time))

		# {"continuous":false,"route":[{"latitude":56.01,"longitude":10.01},{"latitude":56.02,"longitude":10.02}],"propertyTypes":["air_quality"]}

		try:
			coordinates = ','.join(['{"latitude":' + point.split()[1] + ',"longitude":' + point.split()[0] + '}' for point in route.points])
			# print(coordinates)
			request = '{"continuous":false,"route":[' + coordinates + '],"propertyTypes":["air_quality"]}'
			# print(request)
		except Exception, e:
			route.pollutionValues = [0]
			raise ValueError('position not correct %s' % str(e))

		try:
			from websocket import create_connection
			ws = create_connection("ws://localhost:8002/websockets/DataFederation")
			# print "Created connection"
			ws.send(request.encode('utf8'))
			# print "Sent"
			# print "Reeiving..."
			result =  ws.recv()
			# print "Received '%s'" % result

			# result = u'{"result":{"air_quality":["98","21","82","90","18","28","21","8","35","96","70","81"]}}'
			# print(result)

			p = re.compile('\{"result":\{"air_quality":\[(.+?)\]\}\}')
			m = p.match(result.decode('utf8'))
			if m :
				route.pollutionValues = [(int)(value.strip('"')) for value in m.group(1).encode().split(",")]
			else :
				route.pollutionValues = [0]
			# print(route.pollutionValues)

			ws.close()
		except Exception, e:
			route.pollutionValues = [0]
			# print(route.pollutionValues)
			raise IOError('Cannot connect to the DataFederation component: %s' % str(e))

	else :
		print("initPollutionValues already called")


#end.