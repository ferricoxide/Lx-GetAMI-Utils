The `devhost-el7` (CentOS or RHEL 7) template takes the following parameter-values:

* `AmiDistro`: Allows for some automatic setting of values in the template-launched instance. Takes one of two valid values - CentOS or RedHat. Currently, there's no meaningful difference between the two values. They are provided for future capabilities.
* `AmiId`: The ID for the in-region AMI to be used to create EL7-specific RPMs and upload them to an S3 bucket-folder. The selected AMI should include the cloud-init RPM and the AWS CLI.
* `InstanceRole`: The name of the IAM instance-role to bind to the template-launched instance. See the [README_IAM.md](README_IAM.md) document for the minimum permission-set to assign to this role.
* `InstanceType`: The AWS instance-type the template should launch. The `t2.micro` default value is sufficient for all currently-known cases.
* `KeyPairName`: The logical name of the SSH public key to assign to the template-instance's default account. In general, the value selected is not important as there should be no need to login to the instance.
* `LoginUser`: The userid to assign to the launched instance's default/provisioning account. In general, the value selected is not important as there should be no need to login to the instance.
* `NoPublicIp`: Whether or not to assign a public IP to the template-launched instance. In general, this can be set to `true` as there should be no need to login to the instance.
* `NoReboot`: Whether or not the template-launched instance should be rebooted after launch-time tasks are completed. In general, this can be set to `true` as there are no configuration-actions within the template that require a reboot to take effect.
* `RootEBSsize`: The size of the EBS used for the template-launched instance's root disk. In general, this should be left at '20'. This setting provided mostly as a hedge against the EL7 AMI not including sufficient free space to download the source-RPMs.
* `RpmBucket`: The bucket selected for hosting RPMs written by the template-launched instances.
* `SubnetIds`: The subnet that the template should launch the instance into.
* `SecurityGroupIds`: The ID of the network security group to apply to the template-launched instance. In general, the value selected is not important as there should be no need to login to the instance. The only constraint is that the specified `SecurityGroupIds` value(s) should be valid for the specified `SubnetIds` value.
