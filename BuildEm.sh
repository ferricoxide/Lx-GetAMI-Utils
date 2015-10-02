#!/bin/sh
#
# Script to build the downloaded SRPMs
#
#################################################################
RPMDEPS=(
         rpm-build
         dos2unix
        )
BUILDROOT="${HOME}/rpmbuild"


# Set up RPM build environment
function PrepBuildDirs() {
   # Ensure RPM build-tree exists
   test -d ${BUILDROOT} || mkdir -p \
         ${BUILDROOT}/{BUILD,RPMS,SOURCES,SPECS,SRPMS} > /dev/null 2>&1

   # Ensure RPM macro-definition file exists
   test -f ${HOME}/.rpmmacros || echo '%_topdir %(echo $HOME)/rpmbuild' \
      > ${HOME}/.rpmmacros
}

function ExtractSource() {
  local EXTDIR="/tmp/srpm_extract"
  test -d ${EXTDIR} || mkdir -p ${EXTDIR}
  for SRPM in *.src.rpm
  do
     rpm2cpio ${SRPM} | ( cd ${EXTDIR} ; cpio -idv )
  done
}

# Move .spec files to ${BUILDROOT}/SPECS
function HomeSpecs() {
  local SPECFILES="/tmp/srpm_extract/*.spec"
  mv ${SPECFILES} ${BUILDROOT}/SPECS
}

# Move source files to ${BUILDROOT}/SOURCES
function HomeSources() {
   (
      cd /tmp/srpm_extract
      # Move the non-archived sources
      mv $(awk '/^Source/{print $2}' ${BUILDROOT}/SPECS/*.spec | sed '{
         /tar.gz/d
         /.tgz/d
         /.zip/d
      }') ${BUILDROOT}/SOURCES
      # Move the archived sources
      mv * ${BUILDROOT}/SOURCES
   )
}

# Install any missing RPM dependencies
function GetMissingRPMS() {
   for DEPEND in ${RPMDEPS[@]}
   do
      if [[ $(rpm --quiet -q ${DEPEND})$? -ne 0 ]]
      then
         ADDRPMS="${ADDRPMS} ${DEPEND}"
      fi
   
      if [[ "${ADDRPMS}" = "" ]]
      then
         echo "No missing RPMs detected"
      else
         yum install -q -y ${ADDRPMS}
      fi
   done
}

GetMissingRPMS
PrepBuildDirs
ExtractSource
HomeSpecs
HomeSources
