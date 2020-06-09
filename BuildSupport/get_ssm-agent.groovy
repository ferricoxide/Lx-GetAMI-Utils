pipeline {

    agent any

    options {
        buildDiscarder(
            logRotator(
                numToKeepStr: '5',
                daysToKeepStr: '30',
                artifactDaysToKeepStr: '30',
                artifactNumToKeepStr: '3'
            )
        )
        disableConcurrentBuilds()
        timeout(time: 60, unit: 'MINUTES')
    }

    environment {
        AWS_DEFAULT_REGION = "${AwsRegion}"
        AWS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
        REQUESTS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
    }

    parameters {
        string(name: 'AwsCred', description: 'Jenkins-stored AWS credential with which to execute cloud-layer commands')
        string(name: 'AwsRegion', defaultValue: 'us-east-1', description: 'Amazon region to deploy resources into')
        string(name: 'DownloadUrl', defaultValue: 'https://s3.amazonaws.com/ec2-downloads-windows/SSMAgent/latest/linux_amd64/amazon-ssm-agent.rpm', description: 'URL from which to download the ssm-agent binary-RPM')
        string(name: 'StagingBucket', description: 'S3 Bucket in which to upload newly-fetched binary-rpm')
    }

    stages {
        stage ('Grab Binary-RPM') {
            steps {
                deleteDir()
                sh '''#!/bin/bash
                   set -euo pipefail

                   printf "Attempting to fetch current amazon-ssm-agent version... "
                   curl -sOkL "${DownloadUrl}" && echo "Success" || echo "Failed"

                   NEWNAME="$( rpm -qp amazon-ssm-agent.rpm --qf '%{name}-%{version}-%{release}.el7.%{arch}.rpm' )"

                   printf "Renaming download to %s... " "${NEWNAME}"
                   mv amazon-ssm-agent.rpm \${NEWNAME}  && echo "Success" || echo "Failed"

                   cp \${NEWNAME} \${NEWNAME/el7/el8}
                '''
            }
        }
        stage ('Stage Unsigned-RPM') {
            steps {
                withCredentials(
                    [
                        [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
                    ]
                ) {
                    sh '''#!/bin/bash
                       set -euo pipefail

                       UPLOADFILES=( \$( stat -c '%n' amazon-ssm-agent*el{7,8}.x86_64.rpm ) )

                       echo "Files to upload: \${UPLOADFILES[*]}"

                       for UPLOADFILE in \${UPLOADFILES[*]}
                       do
                          echo "Checking for existence of s3://${StagingBucket}\${UPLOADFILE} ..."

                          if [[ $( aws s3 ls "s3://${StagingBucket}\${UPLOADFILE}" > /dev/null 2>&1 )$? -eq 0 ]]
                          then
                             echo "\${UPLOADFILE} already exists in s3://${StagingBucket}"
                          else
                             printf "copying \${UPLOADFILE} to s3://${StagingBucket} ...\n"
                             aws s3 cp \${UPLOADFILE} s3://${StagingBucket}\${UPLOADFILE} && \
                               echo "Success" || ( echo "Failed" ; exit 1)
                          fi
                       done
                    '''
                }
            }
        }
    }
}
