# Fetching Source RPMs
Amazon-hosted instances benefit from the availability supplemental tools provided by Amazon. While Amazon has begin to maintain some utilities via public GitHub projects, not all are yet directly availabile for download and porting. Further, the easiest-to-automate method for fetching updates has proven to be: 

1. Instantiate an Amazon Linux AMI
2. Identify the AWS tools to be ported
3. Use the `yumdownloader` tool's `--source` flag to download the desired utilities' source-RPM packages
4. Copy the SRPMs to your target installation environment (e.g., "CentOS 7")
5. Use the `rpmbuild` utility (found in the "Development tools" Yum package-group) to create RPMs from the SRPMs.
7. (Optional) Sign the resultant binary RPMs
6. Install the resultant RPMs to desired Enterprise Linux instances.

As of the creation of this RPM, the following AWS-related packages were available for pull-down using the `yumdownloader --source` method:

* amazon-ecr-credential-helper
* amazon-efs-utils
* amazon-linux-extras
* amazonlinux-indexhtml
* amazon-linux-onprem
* amazon-ssm-agent
* aws-amitools-ec2
* aws-apitools-as
* aws-apitools-cfn
* aws-apitools-common
* aws-apitools-ec2
* aws-apitools-elb
* aws-apitools-mon
* aws-cfn-bootstrap
* awscli
* aws-cli-plugin-cloudwatch-logs
* awslogs
* ec2-hibinit-agent
* ec2-instance-connect
* ec2-net-utils
* ec2sys-autotune
* ec2-utils

**Note:** the `amazon-ssm-agent` package currently updates more-frequently than the packages published in the source-RPM yum repository. To facilitate keeping this package at "latest and greatest" level, this project includes a Jenkins job-definition, [get_ssm-agent.groovy](BuildSupport/get_ssm-agent.groovy). This job is designed to be run daily and will upload the latest-available binary-RPMs to a specified S3 bucket/folder from the associated GitHub project.

# Update Scheduling
Most of the AWS packages change fairly infrequently. It's possible to subscribe to an SNS topic [`arn:aws:sns:us-east-1:137112412989:amazon-linux-ami-updates`](https://aws.amazon.com/amazon-linux-ami/2016.09-release-notes) that will result in receiving notice any time Amazon releases an updated AMI. AMI updates typically include updates to one or more of the above existing RPMs or release of new RPMs.

# Build Aids:

To facilitate generating updated RPMs (as they are published), this project includes four CloudFormation (CFn) templates in the [BuildSupport](BuildSupport) directory:

* [`IAM-staging_bucket.tmplt.json`](BuildSupport/IAM-staging_bucket.tmplt.json): Template to generate the necessary IAM privileged to be used by the remaining templates
* [`EC2-amzn_linux_2.tmplt.json`](BuildSupport/EC2-amzn_linux_2.tmplt.json): Template to automate the launching of an Amazon AMI that fetches the source-RPMs and stages them to an S3 bucket.
* [`EC2-el7_builder.tmplt.json`](BuildSupport/EC2-el7_builder.tmplt.json): Template to automate the launching of a CentOS 7 AMI that fetches the staged source-RPMs, creates binary-RPMs then stages the binary-RPMs back to S3 (for optional signing)
* [`parent.tmplt.json`](BuildSupport/parent.tmplt.json): Template that wraps the prior three templates and enforces an appropriate execution-order.

# Notes:

* The `aws-cli` RPM is not relevant to Red Hat or CentOS 6/7. It is recommended to get its functionality via the [install-bundle ZIP](http://docs.aws.amazon.com/cli/latest/userguide/installing.html).
* Installation of these RPMs will pull in a number of dependences - mostly related to Java, Ruby and X11.
* When attempting to build or install the RPMs, ensure that the build/install host has appropriate yum repositories enabled to satisfy the pulled-in dependencies.

