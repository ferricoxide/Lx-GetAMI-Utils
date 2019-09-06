pipeline {

    agent any

    options {
        buildDiscarder(
            logRotator(
                numToKeepStr: '5',
                daysToKeepStr: '30',
                artifactDaysToKeepStr: '30',
                artifactNumToKeepStr: '5'
            )
        )
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        CheckHorizon = 7
        AWS_DEFAULT_REGION = "${AwsRegion}"
        AWS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
        REQUESTS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
    }

    parameters {
        string(name: 'NotifyEmail', description: 'Email-recipient for job-status notifications')
        string(name: 'AwsRegion', defaultValue: 'us-east-1', description: 'Amazon region to deploy resources into')
        string(name: 'AwsCred', description: 'Jenkins-stored AWS credential with which to execute cloud-layer commands')
        string(name: 'StackRoot', description: 'Name to give to parent CFn stack')
        string(name: 'Ec2Subnet', description: 'Subnet to launch EC2 instance into')
        string(name: 'SecurityGroups', description: 'Subnet to launch EC2 instance into')
        string(name: 'KeyPairName', description: 'SSH Keypair-name to use for rescue-access')
        string(name: 'IamTemplateUrl', description: 'URL of S3-hosted IAM CFn template')
        string(name: 'FetcherTemplateUrl', description: 'URL of S3-hosted fetcher-instance CFn template')
        string(name: 'BuildTemplateUrl', description: 'URL of S3-hosted builder-instance CFn template')
        string(name: 'BuilderAmi', description: 'AMI-ID to launch builder-instance from')
        string(name: 'BuilderHostname', description: 'Hostname to assign to builder-instance')
        string(name: 'WorkBucket', description: 'S3 bucket to push work-files to')
    }

    stages {
        stage ('Prepare AWS Environment') {
            steps {
                withCredentials(
                    [
                        [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
                    ]
                ) {
                    sh '''#!/bin/bash

                        AMZNAMI="\$( aws ec2 describe-images --owner amazon --filters 'Name=name,Values=amzn2-ami-hvm-2.*-x86_64-gp2' --query 'Images[?CreationDate >= `'\$( date --date="${CheckHorizon} days ago" '+%F' )'`].ImageId' --output text )"

                        # Ensure there was a recently-published Amazon AMI
                        if [[ -z ${AMZNAMI} ]]
                        then
                           echo "No Amazon Linux 2 AMI pushed within the expected period"
                           exit
                        else
                           printf "Amazon Linux 2 AMI of expected-recency found"
                           printf " [%s]. Continuing... \n" "${AMZNAMI}"
                        fi

                        sed -e 's/__AMZN2_AMI__/'"\${AMZNAMI}"'/' \
                            -e 's/__BUILDER_HOSTNAME__/'"${BuilderHostname}"'/'\
                            -e 's/__BUCKET_NAME__/'"${WorkBucket}"'/' \
                            -e 's/__BUILDER_AMI__/'"${BuilderAmi}"'/' \
                            -e 's#__BUILDER_TEMPLATE__#'"${BuildTemplateUrl}"'#' \
                            -e 's#__FETCHER_TEMLATE__#'"${FetcherTemplateUrl}"'#' \
                            -e 's#__IAM_TEMPLATE__#'"${IamTemplateUrl}"'#' \
                            -e 's/__KEYPAIR_NAME__/'"${KeyPairName}"'/' \
                            -e 's/__SGID__/'"${SecurityGroups}"'/'\
                            -e 's/__SUBNET_ID__/'"${Ec2Subnet}"'/' \
                           BuildSupport/parms.json > parent.parms

                        # Delete any blocking stacks
                        echo "Ensure there's no blocking stacks..."
                        aws cloudformation delete-stack --stack-name ${StackRoot}
                        aws cloudformation wait stack-delete-complete --stack-name ${StackRoot}
                    '''
                }
            }
        }
        stage ('Launch Stack') {
            steps {
                withCredentials(
                    [
                        [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
                    ]
                ) {
                    sh '''#!/bin/bash
                        if [[ -f parent.parms ]]
                        then
                           echo "Attempting to create stack ${StackRoot}..."
                           aws cloudformation create-stack --capabilities CAPABILITY_NAMED_IAM \
                              --disable-rollback --stack-name ${StackRoot} \
                              --template-body file://BuildSupport/parent.tmplt.json \
                              --parameters file://parent.parms
                        else
                           touch .no-build
                           echo "Could not find parent.parms file"
                           exit 1
                        fi
                    '''
                }
            }
        }
        stage ('Check Stack Create-Status') {
            steps {
                withCredentials(
                    [
                        [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
                    ]
                ) {
                    sh '''#!/bin/bash
                        # Abort if there was no build
                        if [[ -f .no-build ]]
                        then
                           echo "No stack-creation was successfully started (nothing to do)"
                           exit
                        fi

                        sleep 15

                        # Loop while we wait for stack-create to exit
                        while [[ $(
                                    aws cloudformation describe-stacks \
                                      --stack-name ${StackRoot} \
                                      --query 'Stacks[].{Status:StackStatus}' \
                                      --out text 2> /dev/null | \
                                    grep -q CREATE_IN_PROGRESS
                                   )$? -eq 0 ]]
                        do
                           printf "%s: " "\$( date '+%F-%T' )"
                           echo "Waiting for stack ${StackRoot} to finish create process..."
                           sleep 30
                        done

                        # Print out how the stack-launch ended
                        if [[ $(
                                 aws cloudformation describe-stacks \
                                   --stack-name ${StackRoot} \
                                   --query 'Stacks[].{Status:StackStatus}' \
                                   --out text 2> /dev/null | \
                                 grep -q CREATE_COMPLETE
                                )$? -eq 0 ]]
                        then
                           echo "Stack-creation successful"
                           touch .build-success
                        else
                           echo "Stack-creation ended with non-successful state"
                           exit 1
                        fi
                    '''
                }
            }
        }
        stage ('Cleanup AWS') {
            steps {
                withCredentials(
                    [
                        [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
                    ]
                ) {
                    sh '''#!/bin/bash
                        if [[ -f .build-success ]]
                        then
                           echo "Initiating deletion of stack '${StackRoot}'..."
                           aws cloudformation delete-stack --stack-name ${StackRoot}

                           echo "Waiting for deletion of stack '${StackRoot}'..."
                           aws cloudformation wait stack-delete-complete --stack-name ${StackRoot} \
                             && echo "Delete succeeded"
                        else
                           echo "Nothing to do."
                        fi
                    '''
                }
            }
        }
    }

    post {
        failure {
            mail to: "${env.NotifyEmail}",
                subject: "Failed Pipeline: ${currentBuild.fullDisplayName}",
                body: "Something is wrong with ${env.BUILD_URL}"
        }

        cleanup {
            echo "Executing post-job workspace-cleanup"
            cleanWs()
        }
    }
}
