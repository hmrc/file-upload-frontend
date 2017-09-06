## TEST-ONLY ENDPOINTS
These are endpoints used for testing purposes only and are not available in production. <i>**PLEASE DO NOT USE WITHOUT PERMISSION**</i>

### ENVELOPE TEST-ONLY

#### CREATE ENVELOPE (DO NOT USE) 
-- TODO move to backend documentation

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

#### DOWNLOAD FILE FROM ENVELOPE
Download a file from a given envelope.

```
GET      /file-upload/test-only/download-file/envelopes/{envelopeId}/files/{fileId}/content 
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully returns a list of envelopes.|
| Not Found | 404   |  File not found. |  

#### EXAMPLE
Request (GET): localhost:8899/file-upload/test-only/download-file/envelopes/0b215e97-11d4-4575-91db-c067e74fc111/files/file-id-2324/content

Response: Binary file which contains the selected file.

#### GET EVENTS
Get information about all current events.

```
GET     /file-upload/test-only/events/{streamId}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully returns a list of envelopes.|
| Not Found | 404   |  Envelope with id not found. | 

#### EXAMPLE
Request (GET): localhost:8899/file-upload/test-only/events/stream-id-32456

Response (in Body):
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
| Ok  | 200   | Successfully returns a list of files that are still in progress.

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
    "fileId": "2",
    "startedAt": 1477491112228
  },
  {
    "_id": "bcbbc597-a8a4-4870-bd6c-cfea52ab2ced",
    "envelopeId": "a1752950-32ab-4bdb-a918-0ee9141ac305",
    "fileId": "3",
    "startedAt": 1477491307267
  }
]
```

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

### ROUTING TEST-ONLY

#### CREATE FILE ROUTING REQUEST (DO NOT USE)
Changes the status of an envelope to CLOSED and auto generates a routing Id. The status change, rejects any requests to add files to the envelope. This makes it available for file transfer. To make the request, the envelope Id, application and destination must be provided. A response is provided in the Location Header with a link and a new autogenerated routing Id.
```
POST    /file-upload/test-only/routing/requests
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


#### THE FOLLOWING ENDPOINTS ARE MAINLY USED FOR TESTING CONNECTIVITY WITH S3

##### GET QUARANTINED FILES
Get the list of files that are in the quarantine bucket

```
GET         /file-upload/test-only/s3/quarantine/files
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully retrieves the list of files|

#### EXAMPLE
Request (GET): localhost:8899/file-upload/test-only/s3/quarantine/files

Response (in Body):
```json
[
  // TODO add sample response
]
```

##### GET TRANSIENT FILES
Get the list of files that are in the transient bucket

```
GET         /file-upload/test-only/s3/transient/files
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully retrieves the list of files|

#### EXAMPLE
Request (GET): localhost:8899/file-upload/test-only/s3/transient/files

Response (in Body):
```json
[
  // TODO add sample response
]
```

##### UPLOAD FILE TO THE QUARANTINE BUCKET
Upload a file to the quarantine bucket

```
POST        /file-upload/test-only/s3/quarantine/files/{*fileName}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully retrieves the list of files|

#### EXAMPLE
Request (POST): localhost:8899/file-upload/test-only/s3/quarantine/files/{*fileName}

Body:
``` json
{
    // TODO add sample body
}
```

Response (in Body):
```json
[
  // TODO add sample response
]
```

##### UPLOAD FILE TO THE TRANSIENT BUCKET
Upload a file to the transient bucket

```
POST        /file-upload/test-only/s3/transient/files/{*fileName}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully retrieves the list of files|

#### EXAMPLE
Request (POST): localhost:8899/file-upload/test-only/s3/transient/files/{*fileName}

Body:
``` json
{
    // TODO add sample body
}
```

Response (in Body):
```json
[
  // TODO add sample response
]
```

##### GET FILE FROM QUARANTINE
Get a single file from the quarantine bucket

```
GET         /file-upload/test-only/s3/quarantine/files/*fileName
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully retrieves the file|

#### EXAMPLE
Request (GET): localhost:8899/file-upload/test-only/s3/quarantine/files/*fileName

Response (in Body):
```json
[
  // TODO add sample response
]
```

##### GET FILE FROM TRANSIENT
Get a single file from the transient bucket

```
GET         /file-upload/test-only/s3/transient/files/*fileName
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully retrieves the file|

#### EXAMPLE
Request (GET): localhost:8899/file-upload/test-only/s3/transient/files/*fileName

Response (in Body):
```json
[
  // TODO add sample response
]
```

##### COPY FILE FROM QUARANTINE TO TRANSIENT
Copy a single file from the quarantine bucket to the transient bucket

```
GET         /file-upload/test-only/s3/copy-to-transient/:fileName/:versionId
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully retrieves the file|

#### EXAMPLE
Request (GET): localhost:8899/file-upload/test-only/s3/copy-to-transient/{fileName}/{versionId}

Response (in Body):
```json
[
  // TODO add sample response
]
```

##### GET QUARANTINE PROPERTIES
Get the properties of the quarantine bucket

```
GET         /file-upload/test-only/s3/quarantine/properties
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully retrieves the file|

#### EXAMPLE
Request (GET): localhost:8899/file-upload/test-only/s3/quarantine/properties

Response (in Body):
```json
[
  // TODO add sample response
]
```

##### GET TRANSIENT PROPERTIES
Get the properties of the transient bucket

```
GET         /file-upload/test-only/s3/transient/properties
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully retrieves the file|

#### EXAMPLE
Request (GET): localhost:8899/file-upload/test-only/s3/transient/properties

Response (in Body):
```json
[
  // TODO add sample response
]
```

[back to README](../README.md)