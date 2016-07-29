# file-upload-frontend

Frontend for uploading files to the Tax Platform

[![Build Status](https://travis-ci.org/hmrc/file-upload-frontend.svg?branch=master)](https://travis-ci.org/hmrc/file-upload-frontend) [ ![Download](https://api.bintray.com/packages/hmrc/releases/file-upload-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/file-upload-frontend/_latestVersion)

## Run the application locally

To run the application execute

```
sbt run
```

The endpoints below can then be accessed with the base url http://localhost:8899/file-upload/

## Service manager

```
sm --start FILE_UPLOAD_ALL
```

## Endpoints

### Upload a File

This endpoint allows the uploading of a file to the tax platform.

|-|-|
|HTTP method|POST|
|URL path|/upload|

#### Request

The contentType to be defined as `multipart/form-data` with a single filePart be present containing the actual file content.

#### Parameters

|Parameter|Required?|Example|Description|
|---|---|---|---|
|`envelopeId`|Y|`1234567890`|A file-upload service generated envelope identifier. This will be validated against the file-upload service so a valid envelope *must* have been created prior to invoking this endpoint|
|`fileId`|Y|`0987654321`|A file-upload service generated file identifier. This will be validated against the file-upload service so a valid envelope *must* have been created prior to invoking this endpoint|

#### Responses

|Outcome|HTTP Code|Description|
|---|---|---|
|Success|200|Successful|
|Failure|400|Invalid request|

## Create Envelope (for test purposes only)

|-|-|
|HTTP method|POST|
|URL path|/test-only/create-envelope|

#### Responses

|Outcome|HTTP Code|Body|Description|
|---|---|---|---|
|Success|201|```
             {
               "envelopeId": "`envelopeId`"
             }
             ```|Successful|

## Download File (for test purposes only)

|-|-|
|HTTP method|GET|
|URL path|/test-only/download-file/envelope/790ad9cb-d775-44cb-bab9-bcd05b0a3b20/file/1/content|

#### Responses

|Outcome|HTTP Code|Body|Description|
|---|---|---|---|
|Success|200|`fileBody`|Successful|
             
### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
