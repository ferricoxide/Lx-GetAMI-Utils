# Fetching Source RPMs
Amazon-hosted instances benefit from the availability supplemental tools provided by Amazon. Amazon does not generally make these tools directly availabile for download and porting. Instead, it is necessary to:

1. Instantiate an Amazon Linux AMI
2. Identify the AWS tools you wish to port
3. Use the `get_reference_source` Python script to pull copies of the SRPMs from the S3 hosted software repos
4. Copy the SRPMs to your target installation environment (e.g., "CentOS 6")
5. Use the `rpmbuild` utility (found in the "Development tools" Yum package-group) to create RPMs from the SRPMs.
6. Install the resultant RPMs to your instance.

As of the creation of this RPM, the following AWS-related packages were available for pull-down using the `get_reference_source` method:

* aws-amitools-ec2
* aws-apitools-as
* aws-apitools-cfn
* aws-apitools-common
* aws-apitools-ec2
* aws-apitools-elb
* aws-apitools-iam
* aws-apitools-mon
* aws-apitools-rds
* aws-cfn-bootstrap
* aws-cli-plugin-cloudwatch-logs
* awslogs
* aws-scripts-ses
* aws-vpc-nat

# Update Scheduling
Most of the AWS packages change fairly infrequently. It's possible to subscribe to an SNS topic [`arn:aws:sns:us-east-1:137112412989:amazon-linux-ami-updates`](https://aws.amazon.com/amazon-linux-ami/2016.09-release-notes) that will result in receiving notice any time Amazon releases an updated AMI. AMI updates typically include updates to one or more of the above existing RPMs or release of new RPMs.

# Notes:

* The `aws-cli` RPM is not relevant to Red Hat or CentOS 6/7. It is recommended to get its functionality via the [install-bundle ZIP](http://docs.aws.amazon.com/cli/latest/userguide/installing.html).
* If building on CentOS 7, it is recommended to add `%dist .el7` to `${HOME}/.rpmmacros`. Otherwise, RPMs will have `el7.centos` within their Release-name field.
* Installation of these RPMs will pull in a number of dependences - mostly related to Java, Ruby and X11.
* When attempting to build or install the RPMs, ensure that the build/install host has appropriate yum repositories enabled to satisfy the pulled-in dependencies.

# Build Aids:

To help ensure that RPMs are fairly easily created from the SRPMs, the following CloudFormation templates are provided 
* [srpmhost-amzn.tmpl.json](srpmhost-amzn.tmpl.json): Launches an Amazon Linux instance. Uses the `get_reference_source` utility to download source-RPMs for all of the Amazon Linux utilities, then uploads them to S3. See the [README_Template_Amzn.md](Docs/README_Template_Amzn.md) for further information on using the template.
* [devhost-el6.tmpl.json](devhost-el6.tmpl.json): Prepares an EL6-based instance's default user account's `${HOME}/.rpmmacros` file and creates a `${HOME}/rpmbuild` directory and installs the `@development` RPM group plus further, stand-alone RPMs required to successfully build the RPMs. Downloads source-RPMs from S3, creates el6-installable RPMs and loades them back to S3. See the [README_Template_el6.md](Docs/README_Template_el6.md) for further information on using the template.
* [devhost-el7.tmpl.json](devhost-el7.tmpl.json): Prepares an EL7-based instance's default user account's `${HOME}/.rpmmacros` file and creates a `${HOME}/rpmbuild` directory and installs the `@development` RPM group plus further, stand-alone RPMs required to successfully build the RPMs. Downloads source-RPMs from S3, creates el7-installable RPMs and loades them back to S3. See the [README_Template_el7.md](Docs/README_Template_el7.md) for further information on using the template.

