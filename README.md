# file-upload-frontend

Frontend for uploading files to the Tax Platform. Please <i>**DO NOT USE**</i> Test-Only and Internal-Use-Only endpoints <i>**WITHOUT PERMISSION**</i>  

[![Build Status](https://travis-ci.org/hmrc/file-upload-frontend.svg?branch=master)](https://travis-ci.org/hmrc/file-upload-frontend) [ ![Download](https://api.bintray.com/packages/hmrc/releases/file-upload-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/file-upload-frontend/_latestVersion)

## Software Requirements
*   ClamAv Version 0.99 or later - the [clam-av client ReadMe](https://github.com/hmrc/clamav-client) provides documentation on how to install or alternatively you can download and run the [docker-clamav image](https://hub.docker.com/r/mkodockx/docker-clamav). ClamAv is also configured to block Macros.
*   MongoDB Version 3.2 (3.4 will not work currently)
*   Requires an AWS Account

## Run the application locally

Before you attempt to run file-upload-frontend locally ensure:
 
* You have ClamAV running and the correct version of Mongo as per the Software Requirements above.

You can start/stop them with docker compose file - file-upload-compose.yml:

```
version: '3'
services:
  mongo:
    image: mongo:3.2
    ports:
    - "27017:27017"
  clamav:
    image: mkodockx/docker-clamav
    ports:
    - "3310:3310"
```
and bash commands:
```
docker-compose -f file-upload-compose.yml up -d
docker-compose -f file-upload-compose.yml stop

```




### AWS Account Setup Overview:

Once you have your AWS Account setup, you need to create two buckets which is where all files will be stored. See: [Create A Bucket](http://docs.aws.amazon.com/AmazonS3/latest/gsg/CreatingABucket.html). One to represent quarantine and the other for transient. Choose the appropriate region from where you are running the app to avoid data latency: [AWS Regions](http://docs.aws.amazon.com/general/latest/gr/rande.html)

When naming you buckets, please follow the: [Rules of Bucket Naming](http://docs.aws.amazon.com/AmazonS3/latest/dev//BucketRestrictions.html#bucketnamingrules) Enable Versioning during the create bucket process because it is used to generate the FileRedId. For further details see configuration of buckets in: [Working with Amazon S3 Buckets](http://docs.aws.amazon.com/AmazonS3/latest/dev/UsingBucket.html#bucket-config-options-intro)  

Next follow the steps to: [Create an IAM User](http://docs.aws.amazon.com/IAM/latest/UserGuide/id_users_create.html) and ensure Programmatic Access is selected. Then give the permission: AmazonS3FullAccess. This grants the user full access to use all APIs in S3. For more details about permissions see: [AWS IAM Permissions](http://docs.aws.amazon.com/IAM/latest/UserGuide/access_permissions.html)

Once you have created your IAM user, you will have a pair of Access Keys automatically generated: AWS KEY ID and AWS SECRET ACCESS KEY. This is the only time AWS SECRET ACCESS KEY is shown. Only the AWS KEY ID is shown on the user profile. It is preferable to Download the .csv file ands store it somewhere safe. If lost, in the user profile, select "Make Inactive" on the Access Key and generate a new one. 

### How to Run App

By now you have your buckets and your IAM user created. Next you need to configure the following environment variables:

```
  S3_BUCKET_TRANSIENT="replace-with-your-bucket-name-for-transient"
  S3_BUCKET_QUARANTINE="replace-with-your-bucket-name-for-quarantine"
  AWS_KEY="replace-with-your-aws-key"
  AWS_SECRET="replace-with-your-aws-secret"
```

To find your bucket names go to S3. To find your AWS KEY and AWS SECRET, they will be in the .csv file you downloaded as mentioned.

Note: Setting the environment variables in system for S3 will cause the integration tests to fail because they use a mock library.

To run the application execute

```
sbt run
```

Alternatively, you can write up a bash script to have your terminal run with the environment variables mentioned.

```
S3_BUCKET_TRANSIENT=replace-with-your-bucket-name-for-transient \
S3_BUCKET_QUARANTINE=replace-with-your-bucket-name-for-quarantine \
AWS_KEY=replace-with-your-aws-key \
AWS_SECRET="replace-with-your-aws-secret" \
sbt run
```

The endpoints can then be accessed with the base url http://localhost:8899/

## Service manager

```
sm --start FILE_UPLOAD_ALL
```

Note: Does not have AWS.

## Table of Contents

*   [Endpoints](#endpoints)
*   [Optional Redirection](#redirection)
*   [Test-Only Endpoints](#testonly)
*   [Internal-Use-Only Endpoints](#internal)


## Endpoints <a name="endpoints"></a>

### Upload File
Uploads a single file to the envelope via multipart form. 
File constraints (such as file type, max no of files and file size) are managed via the file-upload (back end) service.
If a routing request has been created for an envelope, any attempts after to upload a file will be rejected.

```
POST    /file-upload/upload/envelopes/{envelope-Id}/files/{file-Id}
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | File successfully uploaded.  |
| Bad Request  | 400   | Explanation what went wrong, e.g.: Request must have exactly one file attached. |
| Not Found | 404   |  Envelope not found. |
| Entity Too Large  | 413   |  File size exceeds limit to upload.  |
| Unsupported Media Type  | 415   |  File type other than the supported type.  |
| Locked  | 423   |  Routing request has been made for this Envelope.  |

##### Example
Request (POST): localhost:8899/file-upload/upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/file-id-1

Body (Multipart Form): A single binary file.  
         
Note: If maxSizePerItem is specified in the [envelope](https://github.com/hmrc/file-upload#create-an-envelope), then it is applied when the file is uploaded. Otherwise the default is 10MB. 

Response: 200

##### Optional redirection <a name="redirection"></a>
Upload File With Redirection-URL:
You can provide optional navigation URLs for on success/failure(redirect-success-url/redirect-error-url) cases. They need to be provided as URL querry prameters.
ex.: `s"$UPLOAD_ENDPOINT?redirect-success-url=https://service.gov.uk/foo&redirect-error-url=https://service.gov.uk/bar"`

On error we append to the provided error-url: `s"?errorCode=$ERROR_CODE&reason=$BODY_OF_ERROR_RESPONSE"`.

- The URL must begin with https (can be disabled on local instance).
- The URL must be to a valid tax domain.
- The URL decoration will be sanitized (no request parameters and anchors).
- The URL request parameter's keys i.e. "redirect-success-url". If wrongly entered, they will be ignored and if there are no other errors response 200 will be given.
- both redirection urls are not required.

Response: 301 on any redirection

## TEST-ONLY ENDPOINTS <a name="testonly"></a>
These are endpoints used for testing purposes only and are not available in production. <i>**PLEASE DO NOT USE WITHOUT PERMISSION**</i>

### ENVELOPE TEST-ONLY

#### CREATE ENVELOPE (DO NOT USE)
Creates an envelope and auto generates an Id. The body in the http request must be json. Successful response is provided in the Location Header which will have the link of the newly created envelope.
```
POST   	file-upload/test-only/create-envelope
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 201   | Successfully created envelope. |
| Bad Request | 400   |  Envelope not created. |  

#### EXAMPLE
Request (POST): localhost:8899/file-upload/test-only/create-envelope

Body:
``` json
{
    "callbackUrl": "string representing absolute url",
    "metadata": { "any": "valid json object" }
}
```

Note: All parameters are optional. A [callbackUrl](https://github.com/hmrc/file-upload#callback) (documented in file-upload README) is optional but should be provided in order for the service to provide feedback of the envelope's progress.

Response (in Headers): Location → localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653

#### SHOW ENVELOPE (DO NOT USE)
Shows the envelope and its current details such as status, callbackurl and potentially the current files inside. It should show at least the envelope Id and the status when the envelope was created.
```
GET     file-upload/test-only/envelopes/{envelope-id}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully shows envelope details.  |
| Not Found | 404   |  Envelope with id not found. |  


#### EXAMPLE
Request (GET): localhost:8899/file-upload/test-only/envelopes/0b215e97-11d4-4006-91db-c067e74fc653

Response (in Body):
```json
{
  "id": "0b215e97-11d4-4006-91db-c067e74fc653",
  "callbackUrl": "http://absolute.callback.url",
  "metadata": {
    "anything": "the caller wants to add to the envelope"
  },
  "status": "OPEN"
}
```

#### DOWNLOAD FILE (DO NOT USE)
Download a file from an envelope.
```
GET   	/file-upload/test-only/envelopes/{envelope-id}/files/{file-id}/content
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully download a file.  |
| Not Found | 404   |  File not found. |

#### EXAMPLE
Request (GET): localhost:8899/file-upload/test-only/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/file-id-1/content

Response: Binary file which contains the selected file.

### EVENTS TEST-ONLY

#### SHOW EVENTS OF AN ENVELOPE (DO NOT USE)
Retreives a list of all events based on the stream Id. 
 
```
 GET     file-upload/test-only/events/{stream-Id}
```
 
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully returns a list of events based on stream Id.
 
#### EXAMPLE
Request (GET): localhost:8899/file-upload/test-only/events/0b215e97-11d4-4006-91db-c067e74fc653
 
Response (In Body):
```json
[
  {
    "eventId": "bbf89c47-ec22-4b5a-917a-fa19f9f2005d",
    "streamId": "0b215e97-11d4-4006-91db-c067e74fc653",
    "version": 1,
    "created": 1477490656518,
    "eventType": "uk.gov.hmrc.fileupload.write.envelope.EnvelopeCreated",
    "eventData": {
      "id": "0b215e97-11d4-4006-91db-c067e74fc653"
    }
  },
  {
    "eventId": "07ea4682-0c2c-49f8-b01a-6128c18ed30f",
    "streamId": "0b215e97-11d4-4006-91db-c067e74fc653",
    "version": 2,
    "created": 1477490659794,
    "eventType": "uk.gov.hmrc.fileupload.write.envelope.FileQuarantined",
    "eventData": {
      "id": "0b215e97-11d4-4006-91db-c067e74fc653",
      "fileId": "file-id-1",
      "fileRefId": "82c1e62c-ddca-468f-a1c9-ca9aa97aa0a2",
      "created": 1477490659610,
      "name": "zKv4Qv6366462570363333088.tmp",
      "contentType": "application/octet-stream",
      "metadata": {
        "foo": "bar"
      }
    }
  },
  {
    "eventId": "29ab824b-e045-4281-9e40-4fd572a03078",
    "streamId": "0b215e97-11d4-4006-91db-c067e74fc653",
    "version": 3,
    "created": 1477490660074,
    "eventType": "uk.gov.hmrc.fileupload.write.envelope.EnvelopeSealed",
    "eventData": {
      "id": "0b215e97-11d4-4006-91db-c067e74fc653",
      "routingRequestId": "ec97bbd0-7be5-4727-8d92-441818a63dcd",
      "destination": "DMS",
      "application": "foo"
    }
  }
]
```

#### SHOW FILES INPROGRESS (DO NOT USE)
Returns a list of all files that are inprogress.

```
GET     /file-upload/test-only/files/inprogress 
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully returns a list of envelopes.

#### EXAMPLE
Request (GET): localhost:8899/file-upload/test-only/files/inprogress

Response (in Body):
```json
[
  {
    "_id": "82c1e62c-ddca-468f-a1c9-ca9aa97aa0a2",
    "envelopeId": "7e5d442a-e17d-46c7-b8ce-0d12f2176f7f",
    "fileId": "1",
    "startedAt": 1477490659610
  },
  {
    "_id": "9b96ce3e-df86-4c6c-b737-aef1e4c98741",
    "envelopeId": "eb5ec7d2-c6c4-4cf9-935b-9f1d4061fab5",
    "fileId": "1",
    "startedAt": 1477491112228
  },
  {
    "_id": "bcbbc597-a8a4-4870-bd6c-cfea52ab2ced",
    "envelopeId": "a1752950-32ab-4bdb-a918-0ee9141ac305",
    "fileId": "1",
    "startedAt": 1477491307267
  }
]
```

### ROUTING TEST-ONLY

#### CREATE FILE ROUTING REQUEST (DO NOT USE)
Changes the status of an envelope to CLOSED and auto generates a routing Id. The status change, rejects any requests to add files to the envelope. This makes it available for file transfer. To make the request, the envelope Id, application and destination must be provided. A response is provided in the Location Header with a link and a new autogenerated routing Id.
```
POST    /file-routing/test-only/routing/requests
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Created  | 201   | Successfully created routing request.  |
| Bad Request  | 400   | Failed to create route request. | 

#### EXAMPLE
Request (POST): localhost:8898/file-routing/requests

Body:
``` json
{
	"envelopeId":"0b215e97-11d4-4006-91db-c067e74fc653",
	"application":"application/json",
	"destination":"DMS"
}
```

Response(in Headers): Location -> /file-routing/requests/39e0e07d-7969-44ac-9f9c-4f7cc264b027

### TRANSFER TEST-ONLY

#### DOWNLOAD LIST OF ENVELOPES (DO NOT USE) 
Returns either a list of all available or selected envelopes (via query string) for routing that have the status CLOSED and information from the routing request provided (above). 
```
GET     /file-transfer/test-only/transfer/get-envelopes
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully returns a list of envelopes.

#### EXAMPLE

Request (GET): localhost:8899/file-upload/test-only/transfer/get-envelopes

Note: Returns a list of all available envelopes for transfer.

#### OR

Request (GET): localhost:8899/file-upload/test-only/transfer/get-envelopes?destination=DMS

Note: Returns a list of all available envelopes going to a specific destination.

Response (in Body):
```json
{
  "_links": {
    "self": {
      "href": "http://full.url.com/file-transfer/envelopes?destination=DMS"
    }
  },
  "_embedded": {
    "envelopes": [
      {
        "id": "0b215e97-11d4-4006-91db-c067e74fc653",
        "destination": "DMS",
        "application": "application:digital.forms.service/v1.233",
        "_embedded": {
          "files": [
            {
              "href": "/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/1/content",
              "name": "original-file-name-on-disk.docx",
              "contentType": "application/vnd.oasis.opendocument.spreadsheet",
              "length": 1231222,
              "created": "2016-03-31T12:33:45Z",
              "_links": {
                "self": {
                  "href": "/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/1"
                }
              }
            },
            {
              "href": "/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/2/content",
              "name": "another-file-name-on-disk.docx",
              "contentType": "application/vnd.oasis.opendocument.spreadsheet",
              "length": 112221,
              "created": "2016-03-31T12:33:45Z",
              "_links": {
                "self": {
                  "href": "/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/2"
                }
              }
            }
          ]
        },
        "_links": {
          "self": {
            "href": "/file-transfer/envelopes/0b215e97-11d4-4006-91db-c067e74fc653"
          },
          "package": {
            "href": "/file-transfer/envelopes/0b215e97-11d4-4006-91db-c067e74fc653",
            "type": "application/zip"
          },
          "files": [
            {
              "href": "/files/2"
            }
          ]
        }
      }
    ]
  }
}
```


#### DOWNLOAD ZIP (DO NOT USE)
Downloads a zip file which is the envelope and its contents.
```
GET     /file-upload/test-only/transfer/download-envelope/{envelope-id}
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully download zip. |
| Not Found | 404   |  Envelope not found. |

#### EXAMPLE

Request (GET): localhost:8899/file-upload/test-only/transfer/download-envelope/0b215e97-11d4-4006-91db-c067e74fc653

Response: Binary file contains the zipped files.

#### SOFT DELETE AN ENVELOPE (DO NOT USE)
Changes status of an envelope to DELETED which prevents any service or user from using this envelope.
```
DELETE    /file-upload/test-only/transfer/delete-envelopes/{envelope-id}
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Envelope status changed to deleted.  |
| Bad Request  | 400   |  Invalid request. File |
| Not Found | 404   |  Envelope ID not found. |
| Gone | 410   |  Has Deleted before. |
| Locked | 423   |  Unable to deleted. |

#### EXAMPLE
Request (DELETE): localhost:8899/file-upload/test-only/transfer/delete-envelope/0b215e97-11d4-4006-91db-c067e74fc653

Response: 200

### RECREATE COLLECTIONS TEST ONLY

#### RECREATE COLLECTIONS (DO NOT USE)
Deletes all collections in quarantine and transient. Then recreates the following collections and its indexes.

Quarantine:
*   quarantine.chunks

Transient:
*   envelopes-read-model
*   envelopes.chunks
*   events

```
POST    /file-upload/test-only/recreate-collections
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully deleted and recreate collections in both Quarantine and Transient.  |

#### EXAMPLE
Reques (POST): localhost:8899/file-upload/test-only/recreate-collections

Response: 200

## INTERNAL USE ONLY ENDPOINTS <a name="internal"></a>
The following endpoints are for internal use. <i>**PLEASE DO NOT USE THESE ENDPOINTS WITHOUT PERMISSION**</i>.

#### MANUALLY SCAN FILE (DO NOT USE)
Scans a file if it was unsuccessfully scanned on the first attempt.

```
POST    /admin/scan/envelopes/{envelope-Id}/files/{file-Id}/{file-Ref-Id} 
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully scanned file.  |

#### EXAMPLE
Request (POST): localhost:8899/file-upload/scan/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/file-id-1/file-ref-1

Response: 200

#### MANUALLY SEND FILE TO TRANSIENT (DO NOT USE)
Sends a file that is "marked clean" from Quarantine to Transient.

```
POST    /admin/transfer/envelopes/{envelope-Id}/files/{file-Id}/{file-Ref-Id} 
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully sent file to Transient.  |

#### EXAMPLE
Request (POST): localhost:8899/file-upload/transfer/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/file-id-1/file-ref-1 

Response: 200

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
 
