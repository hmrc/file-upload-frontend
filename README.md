# file-upload-frontend

**This service is now deprecated. If you need to use the functions this service delivers, please use [Upscan service](https://confluence.tools.tax.service.gov.uk/pages/viewpage.action?pageId=101663507) or speak to the owners of Upscan service**

Frontend for uploading files to the Tax Platform. Please <i>**DO NOT USE**</i> Test-Only and Internal-Use-Only endpoints <i>**WITHOUT PERMISSION**</i>

[ ![Download](https://api.bintray.com/packages/hmrc/releases/file-upload-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/file-upload-frontend/_latestVersion)

## Software Requirements
*   ClamAv Version 0.99 or later - the [clam-av client ReadMe](https://github.com/hmrc/clamav-client) provides documentation on how to install or alternatively you can download and run the [docker-clamav image](https://hub.docker.com/r/mkodockx/docker-clamav). ClamAv is also configured to block Macros.
*   Requires an AWS Account

## Run the application locally

Before you attempt to run file-upload-frontend locally ensure:

* You have ClamAV running as per the Software Requirements above.

You can start/stop them with docker compose file - file-upload-compose.yml:

```
version: '3'
services:
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
*   [Internal Endpoints](./docs/internal-endpoints.md)
*   [Test-Only Endpoints](./docs/test-only-endpoints.md)
*   [Admin Endpoints](./docs/admin-endpoints.md)
*   [File Upload Process](https://github.com/hmrc/file-upload/blob/master/docs/file-upload-process.md)


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
You can provide optional navigation URLs for on success/failure(redirect-success-url/redirect-error-url) cases. They need to be provided as URL query prameters.
ex.: `s"$UPLOAD_ENDPOINT?redirect-success-url=https://service.gov.uk/foo&redirect-error-url=https://service.gov.uk/bar"`

On error we append to the provided error-url: `s"?errorCode=$ERROR_CODE&reason=$BODY_OF_ERROR_RESPONSE"`.

- The URL must begin with https (can be disabled on local instance).
- The URL must be to a valid tax domain.
- The URL decoration will be sanitized (no request parameters and anchors).
- The URL request parameter's keys i.e. "redirect-success-url". If wrongly entered, they will be ignored and if there are no other errors response 200 will be given.
- both redirection urls are not required.

Response: 301 on any redirection
