# file-upload-frontend

Frontend for uploading files to the Tax Platform. Please <i>**DO NOT USE**</i> Test-Only and Internal-Use-Only endpoints <i>**WITHOUT PERMISSION**</i>  

[![Build Status](https://travis-ci.org/hmrc/file-upload-frontend.svg?branch=master)](https://travis-ci.org/hmrc/file-upload-frontend) [ ![Download](https://api.bintray.com/packages/hmrc/releases/file-upload-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/file-upload-frontend/_latestVersion)

## Software Requirements
*   ClamAv Version 0.99 or later - the [clam-av client ReadMe](https://github.com/hmrc/clamav-client) provides documentation on how to install or alternatively you can download and run the [docker-clamav image](https://hub.docker.com/r/mkodockx/docker-clamav). ClamAv is also configured to block Macros.
*   MongoDB Version 3.2 (3.4 will not work currently)
*   Requires an AWS Account

## Run the application locally

Before you attempt to run file-upload-frontend locally ensure:
 
* You have ClamAV running and the correct version of Mongo as per the Software Requirements above.

You can start/stop them with docker compose file - file-upload-compose.yml:

```
version: '3'
services:
  mongo:
    image: mongo:3.2
    ports:
    - "27017:27017"
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

*   [Endpoints](./docs/endpoints.md)
*   [Test-Only Endpoints](./docs/test-only-endpoints.md)
*   [Admin Endpoints](./docs/admin-endpoints.md)
