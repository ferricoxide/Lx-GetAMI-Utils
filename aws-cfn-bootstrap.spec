%define name aws-cfn-bootstrap
%define version 1.4
%define unmangled_version 1.4-10
%define release 10

Summary: An EC2 bootstrapper for CloudFormation
Name: %{name}
Version: %{version}
Release: %{release}
Source0: %{name}-%{unmangled_version}.tar.gz
License: Amazon Software License
Group: Development/Libraries
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-buildroot
Prefix: %{_prefix}
BuildArch: noarch
Vendor: AWS CloudFormation <UNKNOWN>
Requires: python >= 2.5 python-daemon
Url: http://aws.amazon.com/cloudformation/

%description
Bootstraps EC2 instances by retrieving and processing the Metadata block of a CloudFormation resource.

%prep
%define          aws_product_name                      cfn-init
%define aws_path              /opt/aws
%define aws_bin_path          %{aws_path}/bin
%define aws_product_name_v    %{aws_product_name}-%{version}-%{release}
%define aws_product_path      %{aws_path}/apitools/%{aws_product_name_v}
%define aws_product_path_link %{aws_path}/apitools/%{aws_product_name}

%setup -n %{name}-%{version}

%build
python setup.py build

%install
python setup.py install --root=$RPM_BUILD_ROOT --record=INSTALLED_FILES --install-scripts=%{aws_product_path}/bin --install-data=%{aws_product_path}


%clean
rm -rf $RPM_BUILD_ROOT

%post
# Upgrade- remove old symlink 
if [ "$1" = "2" ]; then
    %__rm -f %{aws_product_path_link}
    # also remove old init script if it exists
    if [ -e  %{_initrddir}/cfn-hup ]; then
        %__rm -f  %{_initrddir}/cfn-hup
    fi
fi

# Install/Upgrade - Create symlink from versioned 
# directory to product name directory if it doesn't exist
if [ ! -e  %{aws_product_path_link} ]; then
    %__ln_s  ./%{aws_product_name_v}  %{aws_product_path_link}
fi

# Create aws bin directory if it doesn't exist:
if [ ! -d %{aws_bin_path} ]; then
    %__mkdir %{aws_bin_path}
fi

for command in %{aws_product_path_link}/bin/*; do
    %define command_name $(basename $command)
    if [ -e %{aws_bin_path}/%{command_name} ]; then
        if [ "$1" = "2" ]; then
            # Upgrade- remove old symlinks
            %__rm -f %{aws_bin_path}/%{command_name}
        fi
    fi
    if [ ! -h %{aws_bin_path}/%{command_name} ]; then
    # Install relative symlink from generic directory to aws shared directory
        %__ln_s ../apitools/%{aws_product_name}/bin/%{command_name}  %{aws_bin_path}/%{command_name}
    fi
done

# Create link to init script
if [ ! -e %{_initrddir}/cfn-hup ]; then
    %__ln_s %{aws_product_path_link}/init/redhat/cfn-hup  %{_initrddir}/cfn-hup
    %__chmod 755 %{_initrddir}/cfn-hup
fi


%preun
# Uninstall: 
if [ "$1" = "0" ]; then
    #Clean up the symlinks if it points to this version
    if [ "x$(readlink %{aws_product_path_link})" == "x./%{aws_product_name_v}" ]; then
        for command in %{aws_bin_path}/cfn*; do
            if [ "x$(readlink $command)" == "x../apitools/%{aws_product_name}/bin/$(basename $command)" ]; then
                %__rm -f $command
            fi
        done
    fi
fi

%postun
if [ "$1" = "0" ]; then
    if [ ! "$(ls -A %{aws_bin_path})" ]; then
        rmdir %{aws_bin_path}
    fi

    %__rm -f %{aws_product_path_link}

    if [ ! "$(ls -A %{aws_path}/apitools)" ]; then
        rmdir %{aws_path}/apitools
    fi

    if [ ! "$(ls -A %{aws_path})" ]; then
        rmdir %{aws_path}
    fi
fi

%files -f INSTALLED_FILES
%defattr(-,root,root)
