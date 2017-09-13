## FILE UPLOAD PROCESS
Below describes how the “file-upload” process works: -

### LIFE-CYCLE OF AN ENVELOPE - TODO insert link to state diagram

#### Create an envelope
The first step is to create an envelope. The following endpoint is called and the envelope is created (an envelopeId is generated and returned in the responseHeader) : -

- POST       /file-upload/envelopes

[click here for more details](https://github.com/hmrc/file-upload#create-an-envelope)


An envelope can contain one or more files. Once created, an envelope will be in an OPEN state, and will remain in this state until a “routing request” is sent.

#### Upload file(s)
After the envelope has been created, the client can now send files using the below endpoint: -

- POST        /file-upload/upload/envelopes/{envelope-id}/files/{file-id}

envelope-id - the ID that was returned in step 1
file-id - a user generated value. This can be any value the use wishes (file-id’s must be unique within an envelope). One request per file.

[click here for more details](https://github.com/hmrc/file-upload-frontend#upload-file)


#### Send routing request
 Once all files have been uploaded, the client can send a “routing request”: - 

- POST       /file-routing/requests

Once this has been sent, the following will happen: -
the envelope moves into a SEALED state 
the files will be virus-scanned (one at a time)
and the client can no longer upload files to the given envelope

[click here for more details](https://github.com/hmrc/file-upload#create-file-routing-request)

Once all files have been scanned, the envelope moves to a CLOSED state (this means that all files have been viruses scanned and no infected files were found)

#### Delete envelope
The envelope will remain in a CLOSED state until the envelope is DELETED. The envelope is deleted with the following endpoint

DELETE     /file-transfer/envelopes/{envelope-id}
envelope-id - the identifier for the envelope to be deleted (this is a soft delete)

[click here for more details](https://github.com/hmrc/file-upload#soft-delete-an-envelope)


#### Envelope Statuses
See [here](https://github.com/hmrc/file-upload#envelope-statuses) for envelope statuses


### LIFE-CYCLE OF A FILE - TODO insert link to state diagram
- Once uploaded, files go into the QUARANTINE bucket (at this point, the file is in a QUARANTINED state), files remain in this bucket until they have been virus scanned. After virus scanning, if no issues are found, the file moves to a CLEANED state (ready to be moved to the TRANSIENT bucket).

- Once the file is CLEANED, it is moved to the TRANSIENT bucket and the status of the file changes to AVAILABLE.

- If a file is found to have a virus, the file remains in the QUARANTINE bucket and the status changes to INFECTED (click [here](TODO add section about how to recover) for how to recover from this).

#### File Statuses

| Status  | Description  | 
| --------|---------|
| QUARANTINED  |  initial state after upload, file is to be/being virus scanned. File is in the QUARANTINE bucket |
| CLEANED | file has been virus scanned and no issues were found |
| AVAILABLE | file is already is a CLEANED state and has now been moved to the TRANSIENT bucket |
| DELETED | TO BE TESTED |
| INFECTED | file has been virus scanned and an issue was found |

### FAQs
#### Why is my envelope stuck in a SEALED state? And how do I recover from this?
This happens when an envelope contains one or more files that have been found to be infected.

#### What happens to INFECTED files?
If a file is found to be INFECTED, the client has a number of options: - 

##### Get notified
If a callbackUrl was provided, the client will be notified about the infected file, for example: - 
```
  {
    "envelopeId": "0b215ey97-11d4-4006-91db-c067e74fc653",
    "fileId": "file-id-1",
    "status": "ERROR",
    "reason": "VirusDectected"
  }
```
##### Manually check and recover
The client can take the following steps: - 
- Request for the status of the envelope; this will retrieve the current state of the envelope and will list the status of all the files contained within the envelope ([show envelope endpoint](https://github.com/hmrc/file-upload#show-envelope))
- Delete the infected file ([delete file endpoint](https://github.com/hmrc/file-upload#hard-delete-a-file))
- Re-upload files




[back to README](../README.md)