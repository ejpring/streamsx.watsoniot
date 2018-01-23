# Copyright (C) 2017  International Business Machines Corporation
# All Rights Reserved

import sys
import json
import streamsx.rest

####################################################################################################

serviceCredentialsFile = './vcap.json'

####################################################################################################

# stop the Streams instance, if its not already stopped

with open(serviceCredentialsFile) as file: 
    serviceCredentials = json.load(file)
serviceName = serviceCredentials['streaming-analytics'][0]['name']

####################################################################################################

print('stopping Streaming Analytics service ' + serviceName + ' ...')
connection = streamsx.rest.StreamingAnalyticsConnection(vcap_services=serviceCredentials, service_name=serviceName)
service = connection.get_streaming_analytics()
result = service.stop_instance()
print('Streaming Analytics service ' + connection.service_name + ' is ' + result['state'])

sys.exit(0)

