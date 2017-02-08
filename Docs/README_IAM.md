Each of the templates generates an instance that either puts files to the SRPMs bucket-folder or the RPMS/elN bucket-folder. In order for these instances to be able to write to the target folders, an instance-role will need to be applied. The following policy-document describes the minimum privileges needed by the template-generated instances:
~~~
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:ListBucket"
            ],
            "Resource": [
                "arn:aws:s3:::<BUCKET_NAME>"
            ]
        },
        {
            "Effect": "Allow",
            "Action": "s3:ListObject",
            "Resource": [
                "arn:aws:s3:::<BUCKET_NAME>",
                "arn:aws:s3:::<BUCKET_NAME>/*"
            ]
        },
        {
            "Effect": "Allow",
            "Action": [
                "s3:PutObject",
                "s3:GetObject",
                "s3:DeleteObject",
                "s3:ListObject"
            ],
            "Resource": [
                "arn:aws:s3:::<BUCKET_NAME>/SRPMS",
                "arn:aws:s3:::<BUCKET_NAME>/SRPMS/*",
                "arn:aws:s3:::<BUCKET_NAME>/RPMS/el6",
                "arn:aws:s3:::<BUCKET_NAME>/RPMS/el6/*",
                "arn:aws:s3:::<BUCKET_NAME>/RPMS/el7",
                "arn:aws:s3:::<BUCKET_NAME>/RPMS/el7/*"
            ]
        }
    ]
}
~~~
Modify the above `<BUCKET_NAME>` variable to match the real name of the bucket hosting the RPMs.
The templates expect a specific folder-hierarchy under the bucket. There will be no need to modify the above values unless also modifying the templates' contents.
