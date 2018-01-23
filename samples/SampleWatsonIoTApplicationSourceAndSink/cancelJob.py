# Copyright (C) 2017  International Business Machines Corporation
# All Rights Reserved

import os
import sys
import string
import json
import streamsx.rest

####################################################################################################

applicationNamespace = 'com.ibm.streamsx.watsoniot.sample.application'

applicationName = 'SampleWatsonIoTApplicationSourceAndSink'

logDirectory = './logs'

serviceCredentialsFile = './vcap.json'

####################################################################################################

def retrieveJobLogs(job, filename):

    os.makedirs(os.path.dirname(filename), exist_ok=True)

    mimetype = 'application/x-compressed'

    response = job.rest_client.session.get(url=job.applicationLogTrace, stream=True)
    if response.status_code != 200: raise Exception('HTTP GET failed, error ' + str(response.status_code) + ' ' + response.reason)
    if not response.headers['Content-Type'].startswith(mimetype): raise Exception('HTTP GET expected response content of type ' + mimetype + ', got ' + response.headers['Content-Type'])

    with open(filename, 'w+b') as file:
        for chunk in response.iter_content(chunk_size=None):
            file.write(chunk)    
    
    return filename

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
        if job.applicationName == applicationNamespace+'::'+applicationName:
            print('retrieving logs from job ' + job.name + ' ...')
            filename = logDirectory + '/' + job.name.replace('::', '.') + '.tar.gz'
            result = retrieveJobLogs(job, filename)
            print('job logs stored in ' + filename) if result else print('could not store logs for job ' + job.name)
            print('canceling job ' + job.name + ' ...')
            result = job.cancel()
            print( ( 'job ' + job.name + ' canceled' ) if result else ( 'could not cancel job ' + job.name ) )
            
sys.exit(0)






















