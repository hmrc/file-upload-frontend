import boto3
import argparse
from datetime import datetime, timezone, timedelta

s3 = boto3.resource('s3')

def parse_arguments():
    parser = argparse.ArgumentParser(description='Delete old files from S3 bucket.')
    parser.add_argument('bucket', metavar='bucket',
                       help='name of the bucket with files to delete')
    parser.add_argument('--really-delete', dest='really_delete', action='store_const',
                       const=True, default=False,
                       help='sum the integers (default: find the max)')
    parser.add_argument('--min-age-to-delete', dest='min_age', default=31, type=int,
                       help="Minimum age of the file to delete")

    return parser.parse_args()

def list_buckets():
        for bucket in s3.buckets.all():
                print(bucket.name)

def delete_older_than(bucket, threshold, really_delete = False):

    versions = boto3.client('s3').list_object_versions(Bucket = bucket.name, Prefix="9a5a9515-da83-46dd-ba0f-e4153aa01570")["DeleteMarkers"]
    for version in versions:
        print(version)

    present = datetime.now(timezone.utc).astimezone()
    if (really_delete):
        print("Deleting objects from bucket ", bucket, " which are older than", threshold)
    else:
        print("Listing objects from bucket ", bucket, " which are older than", threshold)
    for obj in bucket.objects.all():
            key = obj.key
            age = present - obj.last_modified
            if age > threshold:
                if really_delete:
                    print("Removing object ", key, "\t", age)
                    obj.delete()
                else:
                    print("Object ", key, "\t", age)

args = parse_arguments()
bucket = s3.Bucket(args.bucket)
really_delete = args.really_delete
min_age_days = args.min_age
delete_older_than(bucket, timedelta(days = min_age_days), really_delete = really_delete)
