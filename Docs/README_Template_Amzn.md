The `srpmhost` (Amazon Linux) template takes the following parameter-values:

* `AmiId`: The ID for the in-region AMI to be used to pull down source-RPMs for the Amazon tool RPMs. If one has subscribed to the AMI publication announcements SNS topic, the resultant message will contain a list of AMI IDs, their types and the regions they are hosted in.
* `InstanceRole`: The name of the IAM instance-role to bind to the template-launched instance. See the [README_IAM.md](README_IAM.md) document for the minimum permission-set to assign to this role.
* `KeyPairName`: The logical name of the SSH public key to assign to the template-instance's default account. In general, the value selected is not important as there should be no need to login to the instance.
* `NoPublicIp`: Whether or not to assign a public IP to the template-launched instance. In general, this can be set to `true` as there should be no need to login to the instance.
* `NoReboot`: Whether or not the template-launced instance should be rebooted after launch-time tasks are completed. In general, this can be set to `true` as there are no configuration-actions within the template that require a reboot to take effect.
* `RootEBSsize`: The size of the EBS used for the template-launched instance's root disk. In general, this should be left at '8'. This setting provided mostly as a hedge against the Amazon AMI not including sufficient free space to download the source-RPMs.
* `SubnetIds`: The subnet that the template should launch the instance into.
* `SecurityGroupIds`: The ID of the network security group to apply to the template-launched instance.  In general, the value selected is not important as there should be no need to login to the instance. The only constraint is that the specified `SecurityGroupIds` value(s) should be valid for the specified `SubnetIds` value.
