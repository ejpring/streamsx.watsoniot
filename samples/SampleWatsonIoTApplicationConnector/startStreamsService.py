# Copyright (C) 2017  International Business Machines Corporation
# All Rights Reserved

import sys
import json
import streamsx.rest

####################################################################################################

serviceCredentialsFile = './vcap.json'

####################################################################################################

# start the Streams instance, if its not already started

with open(serviceCredentialsFile) as file: 
    serviceCredentials = json.load(file)
serviceName = serviceCredentials['streaming-analytics'][0]['name']

####################################################################################################

print('starting Streaming Analytics service ' + serviceNyame + ' ...')
connection = streamsx.rest.StreamingAnalyticsConnection(vcap_services=serviceCredentials, service_name=serviceName)
service = connection.get_streaming_analytics()
result = service.start_instance()
print('Streaming Analytics service ' + connection.service_name + ' is ' + result['state'] + ' and ' + result['status'])

sys.exit(0)

