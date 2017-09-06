## INTERNAL-USE/TEMPORARY ENDPOINTS
These are endpoints used for testing purposes only and are not available in production. <i>**PLEASE DO NOT USE WITHOUT PERMISSION**</i>

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


### Download File from mongo
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