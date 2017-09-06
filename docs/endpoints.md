# Table of Contents

*   [Endpoints](#endpoints)
*   [Optional Redirection](#redirection)

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

### Download File from envelope
Download a single file from a given envelope.

```
GET     /file-upload/download/envelopes/{envelopeId}/files/{fileId}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | File successfully downloaded.  |
| Not Found | 404   |  File not found. |

##### Example
Request (GET): localhost:8899/file-upload/download/envelopes/0a656c97-11c4-6483-91db-c067e65fc222/files/file-id-232

Response: Binary file which contains the selected file.


### Download File from mongo --- TODO is this still possible?
Download a single file.

```
GET     /file-upload/download/mongo/files/{fileRefId}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | File successfully downloaded.  |
| Not Found | 404   |  File fileRefId={fileRefId} not found. |

##### Example
Request (GET): localhost:8899/file-upload/download/mongo/files/file-id-232

Response: Binary file which contains the selected file.


### Illegal download from quarantine
Download a single file (with a given envelope) from the quarantine.

```
GET       /file-upload/download/quarantine/envelopes/{envelopeId}/files/{fileId}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | File successfully downloaded.  |
| Not Found | 404   |  File fileRefId={fileRefId} not found. |

##### Example
Request (GET): localhost:8899/file-upload/download/quarantine/envelopes/0a656c97-11c4-2345-91db-c067e65fc675/files/file-id-287

Response: Binary file which contains the selected file.


### Internal Download File from envelope
Download a single file.

```
GET     /internal-file-upload/download/envelopes/{envelopeId}/files/{fileId}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | File successfully downloaded.  |
| Not Found | 404   |  File not found. |

##### Example
Request (GET): localhost:8899/internal-file-upload/download/envelopes/0a656c97-11c4-6483-91db-c067e65fc222/files/file-id-23

Response: Binary file which contains the selected file.


[back to README](../README.md)