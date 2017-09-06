## ADMIN ENDPOINTS
The following endpoints are for internal use. <i>**PLEASE DO NOT USE THESE ENDPOINTS WITHOUT PERMISSION**</i>.

##### GET FILE INFO
Get information about a given file

```
GET         /admin/files/info/:fileRefId 
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully retrieves the file|

#### EXAMPLE
Request (GET): localhost:8899/admin/files/info/:fileRefId

Response (in Body):
```json
{
    "_id": "0b215e97-11d4-4006-91db-c067e74fc653",
    "filename": "the-name-of-the-file.pdf",
    "chunkSize": 10,
    "uploadDate": "1477490659610",
    "length": 32534435,
    "contentType": "application/pdf"
}
```

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


[back to README](../README.md)