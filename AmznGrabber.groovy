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
        string(name: 'AwsSubnetName', defaultValue: '', description: 'Logical name of subnet in which to build EC2s')
        string(name: 'AwsProvkeyName', defaultValue: '', description: 'Name of AWS provisioning-key')
        string(name: 'AwsSecurityGroups', defaultValue: '', description: 'Comma-delimited list of EC2 security-groups to attach to instance')
        string(name: 'GitCred', description: 'Jenkins-stored Git credential with which to execute git commands')
        string(name: 'GitProjUrl', description: 'SSH URL from which to download the Jenkins git project')
        string(name: 'GitProjBranch', description: 'Project-branch to use from the Jenkins git project')

    }

    stages {
        stage ('Task Prep') {
            steps {
                deleteDir()
                writeFile file: '/tmp/amzn-userdata.txt',
                    text: '''#!/bin/bash
yum update -y
yum install -y aws*
yum install -y ec2*
cd /var/tmp
for RPM in $( rpm -qa aws\\* ec2\\* --qf '%{name}\\n' )
do
   yumdownloader --source $RPM
done
chmod a+r /var/tmp/*.rpm
'''
                writeFile file: '/tmp/spel-userdata.txt',
                    text: '''#!/bin/bash
yum update -y
yum install -y @development
'''
                git branch: "${GitProjBranch}",
                    credentialsId: "${GitCred}",
                    url: "${GitProjUrl}"
                withCredentials(
                    [
                        [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                        sshUserPrivateKey(credentialsId: "${GitCred}", keyFileVariable: 'SSH_KEY_FILE', passphraseVariable: 'SSH_KEY_PASS', usernameVariable: 'SSH_KEY_USER')
                    ]
                ) {
                    sh '''#!/bin/bash
                        BUILD_SUBNET="$( aws --region ${AwsRegion} ec2 describe-subnets --filters "Name=tag:Name,Values=${AwsSubnetName}"  --query "Subnets[].SubnetId" --output text)"
                        AMZN1_IMAGE="$( aws --region ${AwsRegion} ec2 describe-images --filters "Name=owner-alias,Values=amazon" "Name=name,Values=amzn-ami-hvm-*-gp2" --query "Images[].{ID:ImageId,Date:CreationDate}" --out text | sort | awk 'END {print $2}' )"
                        AMZN2_IMAGE="$( aws --region ${AwsRegion} ec2 describe-images --filters "Name=owner-alias,Values=amazon" "Name=name,Values=amzn2-ami-hvm-*-gp2" --query "Images[].{ID:ImageId,Date:CreationDate}" --out text | sort | awk 'END {print $2}' )"
                        EL6_IMAGE="$( aws --region ${AwsRegion} ec2 describe-images --filters "Name=name,Values=spel-minimal-centos-6-hvm-*" --query "Images[].{ID:ImageId,Date:CreationDate}" --out text | sort | awk 'END {print $2}' )"
                        EL7_IMAGE="$( aws --region ${AwsRegion} ec2 describe-images --filters "Name=name,Values=spel-minimal-centos-7-hvm-*" --query "Images[].{ID:ImageId,Date:CreationDate}" --out text | sort | awk 'END {print $2}' )"

                        # Output
                        printf "%s;%s;%s;%s;%s\n" "\${BUILD_SUBNET}" "\${AMZN1_IMAGE}" "\${AMZN2_IMAGE}" "\${EL6_IMAGE}" "\${EL7_IMAGE}" > BuildInfo.txt
                    '''
                }
            }
        }
        stage ('Package Sources') {
            steps {
                parallel (
                    Amzn1: {
                        withCredentials(
                            [
                                [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                                sshUserPrivateKey(credentialsId: "${GitCred}", keyFileVariable: 'SSH_KEY_FILE', passphraseVariable: 'SSH_KEY_PASS', usernameVariable: 'SSH_KEY_USER')
                            ]
                        ) {
                            sh '''#!/bin/bash
                                SUBNET="$( cut -d ';' -f 1 BuildInfo.txt )"
                                IMAGEID="$( cut -d ';' -f 2 BuildInfo.txt )"
                                aws --region ${AwsRegion} ec2 run-instances --instance-type t3.micro --subnet-id \${SUBNET} --image-id \${IMAGEID} --user-data file:///tmp/amzn-userdata.txt --key-name ${AwsProvkeyName} --security-group-ids ${AwsSecurityGroups} > amzn1-build.json
                            '''
                        }
                    },
                    Amzn2: {
                        withCredentials(
                            [
                                [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                                sshUserPrivateKey(credentialsId: "${GitCred}", keyFileVariable: 'SSH_KEY_FILE', passphraseVariable: 'SSH_KEY_PASS', usernameVariable: 'SSH_KEY_USER')
                            ]
                        ) {
                            sh '''#!/bin/bash
                                SUBNET="$( cut -d ';' -f 1 BuildInfo.txt )"
                                IMAGEID="$( cut -d ';' -f 3 BuildInfo.txt )"
                                aws --region ${AwsRegion} ec2 run-instances --instance-type t3.micro --subnet-id \${SUBNET} --image-id \${IMAGEID} --user-data file:///tmp/amzn-userdata.txt --key-name ${AwsProvkeyName} --security-group-ids ${AwsSecurityGroups} > amzn2-build.json
                            '''
                        }
                    }
                )

            }
        }
        stage ('Builder Hosts') {
            steps {
                parallel (
                    spel6: {
                        withCredentials(
                            [
                                [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                                sshUserPrivateKey(credentialsId: "${GitCred}", keyFileVariable: 'SSH_KEY_FILE', passphraseVariable: 'SSH_KEY_PASS', usernameVariable: 'SSH_KEY_USER')
                            ]
                        ) {
                            sh '''#!/bin/bash
                                SUBNET="$( cut -d ';' -f 1 BuildInfo.txt )"
                                IMAGEID="$( cut -d ';' -f 4 BuildInfo.txt )"
                                aws --region ${AwsRegion} ec2 run-instances --instance-type t2.micro --subnet-id \${SUBNET} --image-id \${IMAGEID} --user-data file:///tmp/spel-userdata.txt --key-name ${AwsProvkeyName} --security-group-ids ${AwsSecurityGroups} > spel6-build.json
                            '''
                        }
                    },
                    spel7: {
                        withCredentials(
                            [
                                [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                                sshUserPrivateKey(credentialsId: "${GitCred}", keyFileVariable: 'SSH_KEY_FILE', passphraseVariable: 'SSH_KEY_PASS', usernameVariable: 'SSH_KEY_USER')
                            ]
                        ) {
                            sh '''#!/bin/bash
                                SUBNET="$( cut -d ';' -f 1 BuildInfo.txt )"
                                IMAGEID="$( cut -d ';' -f 5 BuildInfo.txt )"
                                aws --region ${AwsRegion} ec2 run-instances --instance-type t3.micro --subnet-id \${SUBNET} --image-id \${IMAGEID} --user-data file:///tmp/spel-userdata.txt --key-name ${AwsProvkeyName} --security-group-ids ${AwsSecurityGroups} > spel7-build.json
                            '''
                        }
                    }
                )
            }
        }
    }
}
