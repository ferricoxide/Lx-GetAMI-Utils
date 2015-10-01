#!/bin/sh
#
# Script to spin up an instance of the latest-available Amazon
# Linux instance from which to extract the various AWS CLI
# utilities and modules
#
#################################################################
KEYNAME="${1:-UNDEF}"
AWSBIN="/opt/aws/bin/aws"
METADATA="http://169.254.169.254/latest/dynamic/instance-identity/document/" 
if [[ -x /usr/bin/jq ]]
then
   export AWS_DEFAULT_REGION=$(curl -s ${METADATA} | jq -r .region)
else
   export AWS_DEFAULT_REGION=$(curl -s ${METADATA} | \
      awk -F":" '/"region"/{ print $2 }' | sed -e 's/^ "//' -e 's/".*$//')
fi
AMZNUTIL="aws-amitools-ec2
          aws-apitools-as
          aws-apitools-common
          aws-apitools-ec2
          aws-apitools-elb
          aws-apitools-mon
          aws-apitools-rds
          aws-cfn-bootstrap
          aws-cli
          ec2-utils
          get_reference_source
          python-boto"

# Connect to AMI and stage SRPM files
function PullSRPMs() {
   # Pull SRPMs to instance
   for PKG in ${AMZNUTIL}
   do
      ssh ec2-user@${INSTANCEDNS} "echo yes | get_reference_source -p $PKG"
   done

   # Pull SRPMs to launch-host
   scp ec2-user@${INSTANCEDNS}:/usr/src/srpm/debug/*.scr.rpm .
}

# Verify provisioning-key's validity
function KeyValid() {
   if [[ $(aws ec2 describe-key-pairs --filters \
           "Name=key-name,Values=${KEYNAME}" --query \
           "KeyPairs[].KeyName" --out text) != "${KEYNAME}" ]]
   then
      echo "Specified key not found" > /dev/stderr
      exit 1
   fi
}

# Look for most-recently published Amazon Linux AMI (HVM)
function LatestAMI() {
   ${AWSBIN} ec2 describe-images --owner amazon --filters \
      "Name=image-type,Values=machine" "Name=architecture,Values=x86_64" \
      "Name=root-device-type,Values=ebs" "Name=name,Values=amzn-ami-hvm-*" \
      --query 'Images[].{ID:ImageId,NAME:Name,DATE:CreationDate}' \
      --output text | sort -n | sed -n '$p' | awk '{print $2}'
}
 
# Make sure the AWScli is installed
if [[ $(aws --version > /dev/null 2>&1)$? -ne 0 ]]
then
   curl -s -L https://s3.amazonaws.com/aws-cli/awscli-bundle.zip \
      -o /tmp/awscli-bundle.zip
   ( cd /tmp ; unzip awscli-bundle.zip )
   sh /tmp/awscli-bundle/install -i /opt/aws -b /usr/bin/aws
fi

# Ensure launch-key is valid
KeyValid

# Select an AMI for launching
echo "Determining latest-available Amazon Linux AMI-ID..."
TARGAMI=$(LatestAMI)

# Launch an instance and collect its ID
LAUNCHED=$(aws ec2 run-instances --image-id ${TARGAMI} --instance-type \
          t2.micro --key-name ${KEYNAME} --query "Instances[].InstanceId" \
          --out text)
# Get new instance's private DNS name
INSTANCEDNS=$(aws ec2 describe-instances --instance-ids ${LAUNCHED} \
              --query "Reservations[].Instances[].PrivateDnsName" --out text)

# Output actionable info
echo "Launched instance ${LAUNCHED} [${INSTANCEDNS}]"

# Pull down updated AWS CLI source-RPMs
###########################
## PullSRPMs
###########################
