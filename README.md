# file-upload-frontend

Frontend for uploading files to the Tax Platform

[![Build Status](https://travis-ci.org/hmrc/file-upload-frontend.svg?branch=master)](https://travis-ci.org/hmrc/file-upload-frontend) [ ![Download](https://api.bintray.com/packages/hmrc/releases/file-upload-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/file-upload-frontend/_latestVersion)

## Run the application locally

To run the application execute

```
sbt run
```

The endpoints can then be accessed with the base url http://localhost:8899/file-upload/

## Service manager

```
sm --start FILE_UPLOAD_ALL
```

## Endpoints
            
[RAML definition](raml/file-upload-frontend.raml)
            
## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
