# Copyright (C) 2017  International Business Machines Corporation
# All Rights Reserved

import sys
import json
import streamsx.rest

####################################################################################################

serviceCredentialsFile = './vcap.json'

####################################################################################################

with open(serviceCredentialsFile) as file: 
    serviceCredentials = json.load(file)
serviceName = serviceCredentials['streaming-analytics'][0]['name']

####################################################################################################

print('connecting to Streaming Analytics service ' + serviceName + ' ...')
connection = streamsx.rest.StreamingAnalyticsConnection(vcap_services=serviceCredentials, service_name=serviceName)
for instance in connection.get_instances():
    print('service ' + connection.service_name + ' is ' + instance.status + ' and ' + instance.health)
    for job in instance.get_jobs():
        print('job ' + job.name + ' is ' + job.status + ' and ' + job.health)

sys.exit(0)
