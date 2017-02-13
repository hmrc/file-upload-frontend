# file-upload-frontend

Frontend for uploading files to the Tax Platform

[![Build Status](https://travis-ci.org/hmrc/file-upload-frontend.svg?branch=master)](https://travis-ci.org/hmrc/file-upload-frontend) [ ![Download](https://api.bintray.com/packages/hmrc/releases/file-upload-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/file-upload-frontend/_latestVersion)

## Run the application locally

To run the application execute

```
sbt run
```

The endpoints can then be accessed with the base url http://localhost:8899/

## Service manager

```
sm --start FILE_UPLOAD_ALL
```

## Endpoint

#### Upload File
Uploads a single file to the envelope via multipart form. The file should not exceed over 11MB. If a routing request has been created for an envelope, any attempts after to upload a file will be rejected.

```
POST    /file-upload/upload/envelopes/{envelopeId}/files/{fileId}
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | File successfully uploaded.  |
| Bad Request  | 400   | Request must have exactly one file attached. |
| Not Found | 404   |  Envelope not found. |
| Entity Too Large  | 413   |  File size exceeds limit to upload.  |
| Locked  | 423   |  Routing request has been made for this Envelope.  |

#### Example
Request (POST): localhost:8899/file-upload/upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/file-id-1

Body (Multipart Form): A single binary file.  
         
Response: 200
            
## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
 