# Copyright (C) 2017  International Business Machines Corporation
# All Rights Reserved

import os
import sys
import time
import json
import streamsx.rest

####################################################################################################

serviceCredentialsFile = './vcap.json'

applicationNamespace = 'com.ibm.streamsx.watsoniot.sample.application'

applicationName = 'SampleWatsonIoTApplicationConnector'

applicationBundle = './output/' + applicationNamespace + '.' + applicationName + '.sab'

applicationCredentialsFile = './WatsonIoTSampleApplication1.credentials.properties'

####################################################################################################

def submitJob(service, sabFile, jobOptions):

    bundleName = os.path.basename(sabFile)
    jobURL = service._get_url('jobs_path')
    jobParameters = { 'bundle_id': bundleName }

    with open(sabFile, 'rb') as bundle:
        jobFiles = [
            ('sab_file', ( bundleName, bundle, 'application/octet-stream' ) ),
            ('job_options', ( 'job_options', json.dumps(jobOptions), 'application/json' ) )
            ]
        return service.rest_client.session.post(url=jobURL, params=jobParameters, files=jobFiles).json()

####################################################################################################

with open(serviceCredentialsFile) as file: 
    serviceCredentials = json.load(file)
serviceName = serviceCredentials['streaming-analytics'][0]['name']

with open(applicationCredentialsFile) as file:
    applicationCredentials = ''.join(line.replace('\n', ',').replace(' ', '') for line in file)

jobOverlay = {
    'jobConfigOverlays': [
        {
            'jobConfig': {
                'submissionParameters': [          
                    { 'name': 'applicationCredentials', 'value': applicationCredentials },
                    { 'name': 'subscriptionDeviceType', 'value': 'SampleDeviceType' },
                    { 'name': 'commandInterval', 'value': 10 }, # ... in seconds
                    { 'name': 'deviceInterval', 'value': 60 }, # ... in seconds 
                    { 'name': 'timeoutInterval', 'value': 600 } # ... in seconds 
                    ],
                'tracing': 'info' # ... or 'error' or 'debug' or 'trace'
                },
            "deploymentConfig": {
                'fusionScheme': 'manual', 
                'fusionTargetPeCount': 1
                } } ] }

####################################################################################################

print('connecting to Streaming Analytics service ' + serviceName + ' ...')
connection = streamsx.rest.StreamingAnalyticsConnection(vcap_services=serviceCredentials, service_name=serviceName)
service = connection.get_streaming_analytics()
result = service.start_instance()
if not result['state']=='STARTED': sys.exit('sorry, service not started')
if not result['status']=='running': sys.exit('sorry, service is not running')
instances = connection.get_instances()
if not len(instances)==1: sys.exit('sorry, service instance not found')
instance = instances[0]
print('service ' + connection.service_name + ' is ' + instance.status + ' and ' + instance.health)
if not instance.status=='running': sys.exit('sorry, service is not running')
if not instance.health=='healthy': sys.exit('sorry, service is not healthy')

print('submitting job ...')
result = submitJob(service, applicationBundle, jobOverlay)
if 'status_code' in result: sys.exit('sorry, submit failed: error ' + str(result['status_code']) + ", " + result['description'])
print('job ' + result['name'] + ' submitted')

sys.exit(0)
