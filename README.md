# file-upload-frontend

Frontend for uploading files to the Tax Platform

[![Build Status](https://travis-ci.org/hmrc/file-upload-frontend.svg?branch=master)](https://travis-ci.org/hmrc/file-upload-frontend) [ ![Download](https://api.bintray.com/packages/hmrc/releases/file-upload-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/file-upload-frontend/_latestVersion)

## Requirements

This service is written in [Scala](http://www.scala-lang.org/) and [Play](http://playframework.com/), so needs at least a [JRE] to run.

## Run the application

To run the application execute

```
sbt run
```

and then access the application at

```
http://localhost:9000/file-upload-frontend/
```

## Endpoints

### POST /upload

This endpoint allows the uploading of a file to the tax platform by means of the `file-upload` service.

The calling service *must* ensure that:

* an envelope was defined via the `file-upload` service and an `envelopeId` and `fileId` are registered for that envelope
* the request contains the above valid `envelopeId` and `fileId`

i.e. If being invoked from an HTML form - the service generating the form should ensure that these fields are present
and have valid values assigned by the `file-upload` service.

This endpoint *requires*:
 
* the contentType to be defined as `multipart/form-data` with the below parameters defined alongside the actual file content.
* a single filePart be present containing the data to be uploaded to the corresponding `fileId` within the envelope.

#### Parameters
|Parameter|Required?|Example|Description|
|---|---|---|---|
|`successRedirect`|Y|`http://www.tax.gov.uk/service/uploadSuccess`|*Client side* redirection - fully qualified, external URL. This is used within a 303 SEE OTHER redirect and should indicate the next page within your user journey for a successful upload outcome. NB: This will result in an HTTP `GET` of the indicated endpoint.|
|`failureRedirect`|Y|`http://www.tax.gov.uk/service/uploadFailure`|*Client side* redirection - fully qualified, external URL. This is used within a 303 SEE OTHER redirect and should indicate the next page within your user journey for a failure of upload outcome. NB: This will result in an HTTP `GET` of the indicated endpoint.|
|`envelopeId`|Y|`1234567890`|A file-upload service generated envelope identifier. This will be validated against the file-upload service so a valid envelope *must* have been created prior to invoking this endpoint|
|`fileId`|Y|`0987654321`|A file-upload service generated file identifier. This will be validated against the file-upload service so a valid envelope *must* have been created prior to invoking this endpoint|

#### Responses
|Outcome|Response Code|Definition|Parameters|Description|
|---|---|---|---|---|
|Success|303|`SEE OTHER` -> `successRedirect`|`None`|Returned if the file was uploaded successfully to the service|
|Failure|303|`SEE OTHER` -> `failureRedirect`|`invalidParam` -> `[paramName]` (0-*)|Returned if parameter validation (indicated by 1 or more `invalidParam` parameters) or if the file upload failed (no parameters)|
|Failure|303|`SEE OTHER` -> `REFERER`|`invalidParam` -> `[paramName]` (0-*)|If `failureRedirect` is absent. Returned if parameter validation (indicated by 1 or more `invalidParam` parameters) or if the file upload failed (no parameters)|
|Failure|400|`BAD REQUEST`|`None`|If both `failureRedirect` and `Referer` header are not present|

Note: If no, or more than one fileParts are specified, the special `invalidParam=file` will be returned indicating an error in the request.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")